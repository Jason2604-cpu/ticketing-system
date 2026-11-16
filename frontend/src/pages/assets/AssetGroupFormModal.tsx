import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { Field, Input, Textarea } from '../../components/ui/Input'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { assetGroupsApi } from '../../api/assetGroups'
import { errorMessage, isApiError } from '../../lib/api'
import type { AssetGroup, AssetGroupRequest } from '../../types'

/**
 * Create/edit an asset group's name and description. Members aren't picked here: each asset
 * chooses its group on the asset form. The company is fixed by the page it was opened from.
 */
export function AssetGroupFormModal({ group, companyId, companyName, onClose, onSaved }: {
  group: AssetGroup | null
  companyId: number
  companyName?: string
  onClose: () => void
  onSaved: (saved: AssetGroup | null) => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const editing = group !== null
  const [form, setForm] = useState<AssetGroupRequest>({
    companyId,
    name: group?.name ?? '',
    description: group?.description ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [confirmDelete, setConfirmDelete] = useState(false)

  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['asset-groups'] })
    void qc.invalidateQueries({ queryKey: ['contracts'] })
    void qc.invalidateQueries({ queryKey: ['assets'] })
  }

  const save = useMutation({
    mutationFn: (body: AssetGroupRequest) => (editing ? assetGroupsApi.update(group.id, body) : assetGroupsApi.create(body)),
    onSuccess: (saved) => {
      invalidate()
      toast(editing ? 'Group updated' : 'Group created')
      onSaved(saved)
    },
    onError: (e) => {
      setError(errorMessage(e))
      setFieldErrors(isApiError(e) ? (e.fields ?? {}) : {})
    },
  })

  const remove = useMutation({
    mutationFn: () => assetGroupsApi.remove(group!.id),
    onSuccess: () => {
      invalidate()
      toast('Group deleted')
      onSaved(null)
    },
    onError: (e) => { setConfirmDelete(false); setError(errorMessage(e)) },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    if (!form.name.trim()) { setError('Give the group a name'); return }
    save.mutate({ ...form, name: form.name.trim(), description: form.description?.trim() || undefined })
  }

  return (
    <Modal
      open
      onClose={onClose}
      size="md"
      title={editing ? `Edit "${group.name}"` : 'New asset group'}
      footer={
        <div className="flex w-full items-center justify-between gap-2">
          <div>
            {editing && (confirmDelete ? (
              <div className="flex items-center gap-2">
                <span className="text-sm text-muted">Delete this group? Its assets are kept, just ungrouped.</span>
                <Button variant="secondary" onClick={() => setConfirmDelete(false)}>No</Button>
                <Button variant="danger" loading={remove.isPending} onClick={() => remove.mutate()}>Delete</Button>
              </div>
            ) : (
              <Button variant="secondary" onClick={() => setConfirmDelete(true)}><Trash2 size={15} /> Delete</Button>
            ))}
          </div>
          {!confirmDelete && (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={onClose}>Cancel</Button>
              <Button type="submit" form="asset-group-form" loading={save.isPending}>{editing ? 'Save changes' : 'Create group'}</Button>
            </div>
          )}
        </div>
      }
    >
      <form id="asset-group-form" onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {companyName && <p className="text-sm text-muted">Company: <span className="font-medium text-ink">{companyName}</span></p>}

        <Field label="Group name" error={fieldErrors.name}>
          <Input
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            placeholder="DC Rack A4, HQ core network, Finance laptops…"
            invalid={!!fieldErrors.name}
          />
        </Field>
        <Field label="Description (optional)">
          <Textarea rows={2} value={form.description ?? ''} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
        </Field>
      </form>
    </Modal>
  )
}
