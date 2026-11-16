import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { BookOpen, ChevronLeft, ChevronRight, FolderTree, Plus, Search } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { kbApi } from '../../api/kb'
import { errorMessage } from '../../lib/api'
import { timeAgo } from '../../lib/format'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { cn } from '../../lib/cn'
import type { KbStatus, KbVisibility } from '../../types'
import { useKbAccess, useKbRequestedCount } from './kbAccess'
import { ChangesBadge, RequestedBadge, ScopeBadges, StatusBadge, VisibilityBadge } from './KbBadges'

type Tab = 'all' | 'requested' | 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
const TABS: { value: Tab; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'requested', label: 'Needs publishing' },
  { value: 'DRAFT', label: 'Drafts' },
  { value: 'PUBLISHED', label: 'Published' },
  { value: 'ARCHIVED', label: 'Archived' },
]
const PAGE_SIZE = 25

/**
 * Writers' list of working copies — Admin, Engineers and MANAGE_KNOWLEDGE_BASE. An engineer sees
 * the articles they can edit (global ones, and those pinned only to their companies).
 * "Needs publishing" is the managers' queue: drafts an engineer asked to have published.
 */
export default function KbManagePage() {
  const navigate = useNavigate()
  const { isManager } = useKbAccess()
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.value === params.get('tab'))?.value ?? 'all') as Tab
  const [visibility, setVisibility] = useState<KbVisibility | ''>('')
  const [categoryId, setCategoryId] = useState<number | 0>(0)
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim(), 300)
  const [page, setPage] = useState(0)

  const setTab = (t: Tab) => { setPage(0); setParams(t === 'all' ? {} : { tab: t }, { replace: true }) }

  const categories = useQuery({ queryKey: ['kb', 'manage', 'categories'], queryFn: kbApi.manageCategories })
  const requestedCount = useKbRequestedCount(true)
  const filters = {
    status: tab === 'DRAFT' || tab === 'PUBLISHED' || tab === 'ARCHIVED' ? (tab as KbStatus) : undefined,
    requested: tab === 'requested',
    visibility: visibility || undefined,
    categoryId: categoryId || null,
    q: q || undefined,
    page,
    size: PAGE_SIZE,
  }
  const list = useQuery({
    queryKey: ['kb', 'manage', 'list', filters],
    queryFn: () => kbApi.manageList(filters),
    placeholderData: keepPreviousData,
  })
  const data = list.data

  return (
    <>
      <Topbar
        title="Knowledge Base"
        subtitle={isManager ? 'Write, review and publish articles' : 'Write articles and ask for them to be published'}
        search={false}
        newTicket={false}
        actions={
          <div className="flex gap-2">
            <Link to="/kb"><Button variant="secondary"><BookOpen size={16} /> Reader view</Button></Link>
            {isManager && <Link to="/admin/kb/categories"><Button variant="secondary"><FolderTree size={16} /> Categories</Button></Link>}
            <Link to="/admin/kb/new"><Button><Plus size={16} /> New article</Button></Link>
          </div>
        }
      />
      <Page>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="inline-flex rounded-lg border border-line bg-surface p-1">
            {TABS.map((t) => (
              <button key={t.value} type="button" onClick={() => setTab(t.value)}
                className={cn('flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                  tab === t.value ? 'bg-brand-600 text-white' : 'text-gray-700 hover:bg-gray-50')}>
                {t.label}
                {t.value === 'requested' && requestedCount > 0 && (
                  <span className={cn('rounded-full px-1.5 text-xs', tab === t.value ? 'bg-surface/20' : 'bg-purple-100 text-purple-700')}>{requestedCount}</span>
                )}
              </button>
            ))}
          </div>
        </div>

        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="relative min-w-64 flex-1">
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} placeholder="Search titles…" aria-label="Search titles"
              className="h-10 w-full rounded-lg border border-line bg-surface pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
          </div>
          <SelectMenu<KbVisibility | ''>
            className="w-44"
            value={visibility}
            onChange={(v) => { setVisibility(v); setPage(0) }}
            options={[{ value: '', label: 'Any visibility' }, { value: 'INTERNAL', label: 'Internal' }, { value: 'PUBLIC', label: 'Public' }]}
          />
          <SelectMenu<number>
            className="w-52"
            value={categoryId}
            onChange={(v) => { setCategoryId(v); setPage(0) }}
            options={[{ value: 0, label: 'All categories' }, ...(categories.data ?? []).map((c) => ({ value: c.id, label: c.name }))]}
          />
          <span className="text-sm text-muted">{data ? `${data.totalElements} article${data.totalElements === 1 ? '' : 's'}` : ''}</span>
        </div>

        {tab === 'requested' && isManager && (
          <p className="mb-4 text-sm text-muted">Engineers asked for these to be published. Open one to review the changes, then publish it or decline the request.</p>
        )}

        {list.isError ? <Alert>{errorMessage(list.error)}</Alert> : (
          <Card>
            {list.isLoading ? (
              <div className="p-10 text-center text-sm text-muted">Loading…</div>
            ) : !data || data.items.length === 0 ? (
              <EmptyState
                icon={<BookOpen size={22} />}
                title={tab === 'requested' ? 'Nothing waiting to be published' : q || visibility || categoryId ? 'No articles match these filters' : 'No articles yet'}
                hint={tab === 'all' && !q ? 'Start with the question your team answers most often — the fix you keep retyping into tickets.' : undefined}
                action={tab === 'all' && !q ? <Link to="/admin/kb/new"><Button><Plus size={16} /> New article</Button></Link> : undefined}
              />
            ) : (
              <Table>
                <thead>
                  <tr>
                    <Th>Article</Th>
                    <Th>Status</Th>
                    <Th>Visibility</Th>
                    <Th>Category</Th>
                    <Th>Available to</Th>
                    <Th>Updated</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((a) => (
                    <Tr key={a.id} onClick={() => navigate(`/admin/kb/${a.id}/edit`)}>
                      <Td className="max-w-md">
                        <p className="font-medium text-ink">{a.title}</p>
                        {a.summary && <p className="mt-0.5 line-clamp-1 text-xs text-muted">{a.summary}</p>}
                      </Td>
                      <Td>
                        <div className="flex flex-col items-start gap-1">
                          <StatusBadge status={a.status} version={a.publishedVersion} />
                          {a.publishRequestedAt && <RequestedBadge />}
                          {a.hasUnpublishedChanges && !a.publishRequestedAt && <ChangesBadge />}
                        </div>
                      </Td>
                      <Td><VisibilityBadge visibility={a.visibility} /></Td>
                      <Td className="text-muted">{a.category?.name ?? '—'}</Td>
                      <Td><ScopeBadges companies={a.companies} /></Td>
                      <Td className="whitespace-nowrap text-muted">
                        <span title={new Date(a.updatedAt).toLocaleString()}>{timeAgo(a.updatedAt)}</span>
                        {a.updatedBy && <span className="block text-xs">{a.updatedBy.name}</span>}
                      </Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card>
        )}

        {data && data.totalPages > 1 && (
          <div className="mt-3 flex items-center justify-end gap-2 text-sm text-muted">
            <span>Page {data.page + 1} of {data.totalPages}</span>
            <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page"><ChevronLeft size={15} /></Button>
            <Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page"><ChevronRight size={15} /></Button>
          </div>
        )}
      </Page>
    </>
  )
}
