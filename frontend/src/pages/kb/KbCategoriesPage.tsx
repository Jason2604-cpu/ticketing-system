import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, FolderTree, Pencil, Plus, Trash2 } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Field, Input } from '../../components/ui/Input'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { useToast } from '../../components/ui/useToast'
import { kbApi } from '../../api/kb'
import { errorMessage } from '../../lib/api'
import { cn } from '../../lib/cn'
import type { KbCategory, KbCategoryRequest } from '../../types'
import { CategoryIcon } from './KbBadges'
import { KB_ICON_NAMES } from './kbIcons'

/** Admin / MANAGE_KNOWLEDGE_BASE: the topics readers browse by. One level, no nesting. */
export default function KbCategoriesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState<KbCategory | 'new' | null>(null)
  const categories = useQuery({ queryKey: ['kb', 'manage', 'categories'], queryFn: kbApi.manageCategories })
  const invalidate = () => void qc.invalidateQueries({ queryKey: ['kb'] })

  const remove = useMutation({
    mutationFn: (c: KbCategory) => kbApi.removeCategory(c.id),
    onSuccess: () => { invalidate(); toast('Category deleted') },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const rows = categories.data ?? []
  return (
    <>
      <Topbar
        title="Knowledge Base categories"
        subtitle="The topics readers browse by"
        search={false}
        newTicket={false}
        actions={
          <div className="flex gap-2">
            <Link to="/admin/kb"><Button variant="secondary"><ArrowLeft size={16} /> Articles</Button></Link>
            <Button onClick={() => setEditing('new')}><Plus size={16} /> New category</Button>
          </div>
        }
      />
      <Page>
        {categories.isError ? <Alert>{errorMessage(categories.error)}</Alert> : (
          <Card>
            {categories.isLoading ? <div className="p-10 text-center text-sm text-muted">Loading…</div>
              : rows.length === 0 ? (
                <EmptyState icon={<FolderTree size={22} />} title="No categories yet"
                  hint="A handful of broad topics works best — Email, Printing, Network, Accounts. Articles without one show as Uncategorised."
                  action={<Button onClick={() => setEditing('new')}><Plus size={16} /> New category</Button>} />
              ) : (
                <Table>
                  <thead>
                    <tr><Th>Category</Th><Th>Description</Th><Th className="text-right">Order</Th><Th className="text-right">Articles</Th><Th /></tr>
                  </thead>
                  <tbody>
                    {rows.map((c) => (
                      <Tr key={c.id}>
                        <Td>
                          <span className="flex items-center gap-3">
                            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-50 text-brand-600"><CategoryIcon name={c.icon} size={16} /></span>
                            <span className="font-medium">{c.name}</span>
                          </span>
                        </Td>
                        <Td className="max-w-md text-muted">{c.description || '—'}</Td>
                        <Td className="text-right text-muted">{c.sortOrder}</Td>
                        <Td className="text-right">{c.articleCount}</Td>
                        <Td className="w-24 text-right">
                          <button className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" aria-label={`Edit ${c.name}`} onClick={() => setEditing(c)}><Pencil size={15} /></button>
                          <button className="rounded-md p-1.5 text-muted hover:bg-red-50 hover:text-red-600" aria-label={`Delete ${c.name}`}
                            onClick={() => confirm(c.articleCount
                              ? `Delete "${c.name}"? Its ${c.articleCount} article${c.articleCount === 1 ? '' : 's'} become Uncategorised — nothing else changes.`
                              : `Delete "${c.name}"?`) && remove.mutate(c)}>
                            <Trash2 size={15} />
                          </button>
                        </Td>
                      </Tr>
                    ))}
                  </tbody>
                </Table>
              )}
          </Card>
        )}
      </Page>
      {editing && (
        <CategoryModal
          category={editing === 'new' ? null : editing}
          nextOrder={rows.length ? Math.max(...rows.map((r) => r.sortOrder)) + 1 : 0}
          onClose={() => setEditing(null)}
          onSaved={(msg) => { invalidate(); toast(msg); setEditing(null) }}
        />
      )}
    </>
  )
}

function CategoryModal({ category, nextOrder, onClose, onSaved }: {
  category: KbCategory | null; nextOrder: number; onClose: () => void; onSaved: (message: string) => void
}) {
  const [form, setForm] = useState<KbCategoryRequest>({
    name: category?.name ?? '',
    description: category?.description ?? '',
    icon: category?.icon ?? 'BookOpen',
    sortOrder: category?.sortOrder ?? nextOrder,
  })
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: (body: KbCategoryRequest) => (category ? kbApi.updateCategory(category.id, body) : kbApi.createCategory(body)),
    onSuccess: () => onSaved(category ? 'Category updated' : 'Category created'),
    onError: (e) => setError(errorMessage(e)),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!form.name.trim()) { setError('Give the category a name'); return }
    save.mutate({ ...form, name: form.name.trim(), description: form.description?.trim() || undefined })
  }

  return (
    <Modal open onClose={onClose} title={category ? `Edit "${category.name}"` : 'New category'}
      footer={<>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button type="submit" form="kb-category-form" loading={save.isPending}>{category ? 'Save changes' : 'Create category'}</Button>
      </>}>
      <form id="kb-category-form" onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <div className="grid grid-cols-[minmax(0,1fr)_110px] gap-4">
          <Field label="Name"><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={80} placeholder="Email & Outlook" autoFocus /></Field>
          <Field label="Order" hint="Lower first"><Input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) || 0 })} /></Field>
        </div>
        <Field label="Description (optional)" hint="One line under the name on the Help Centre tiles">
          <Input value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} maxLength={200} />
        </Field>
        <div>
          <span className="mb-1.5 block text-sm font-medium">Icon</span>
          <div className="grid grid-cols-9 gap-1.5">
            {KB_ICON_NAMES.map((name) => (
              <button key={name} type="button" title={name} aria-label={name} aria-pressed={form.icon === name}
                onClick={() => setForm({ ...form, icon: name })}
                className={cn('flex h-9 items-center justify-center rounded-lg border transition-colors',
                  form.icon === name ? 'border-brand-500 bg-brand-50 text-brand-600' : 'border-line text-muted hover:bg-gray-50 hover:text-ink')}>
                <CategoryIcon name={name} size={16} />
              </button>
            ))}
          </div>
        </div>
      </form>
    </Modal>
  )
}
