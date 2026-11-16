import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { Checkbox, Field, Input, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { assetsApi } from '../../api/assets'
import { assetGroupsApi } from '../../api/assetGroups'
import { errorMessage, isApiError } from '../../lib/api'
import type { Asset, AssetRequest, AssetStatus, AssetType } from '../../types'
import { ASSET_STATUSES, ASSET_TYPES } from './assetMeta'
import { AssetAssignmentField, type AssignmentValue } from './AssetAssignmentField'

/** Types that normally belong to one identifiable person. A hint for the form, never a rule. */
const PERSONAL: AssetType[] = ['LAPTOP', 'WORKSTATION']

const blank = (companyId: number): AssetRequest => ({
  companyId,
  name: '',
  assetTag: '',
  type: 'OTHER',
  make: '',
  model: '',
  serialNumber: '',
  location: '',
  groupId: null,
  status: 'ACTIVE',
  assignedTo: 'SHARED',
  ownerId: null,
  guestName: '',
  guestEmail: '',
  purchaseDate: null,
  warrantyStart: null,
  warrantyEnd: null,
  notes: '',
  warrantyReminderEnabled: false,
  warrantyReminderDays: 30,
})

const fromAsset = (a: Asset): AssetRequest => ({
  companyId: a.companyId,
  name: a.name,
  assetTag: a.assetTag ?? '',
  type: a.type,
  make: a.make ?? '',
  model: a.model ?? '',
  serialNumber: a.serialNumber ?? '',
  location: a.location ?? '',
  groupId: a.groupId ?? null,
  status: a.status,
  assignedTo: a.assignedTo,
  ownerId: a.ownerId ?? null,
  guestName: a.guestName ?? '',
  guestEmail: a.guestEmail ?? '',
  purchaseDate: a.purchaseDate ?? null,
  warrantyStart: a.warrantyStart ?? null,
  warrantyEnd: a.warrantyEnd ?? null,
  notes: a.notes ?? '',
  warrantyReminderEnabled: a.warrantyReminderEnabled,
  warrantyReminderDays: a.warrantyReminderDays ?? 30,
})

/**
 * Create or edit one asset.
 *
 * THE COMPANY IS NOT A FIELD. It comes from the page you opened this on, which is the whole
 * reason asset management is company-first: an asset filed against the wrong customer breaks the
 * contract link, the ticket link and that customer's own view at once, and the cheapest fix is to
 * make the mistake unreachable rather than to validate against it. The company is shown, not
 * chosen.
 */
export function AssetFormModal({ asset, companyId, companyName, onClose, onSaved }: {
  asset: Asset | null
  companyId: number
  companyName?: string
  onClose: () => void
  onSaved: (saved: Asset) => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const editing = asset !== null
  const [form, setForm] = useState<AssetRequest>(asset ? fromAsset(asset) : blank(companyId))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  // Once someone sets the assignment themselves, the type-based hint below stops interfering.
  const [assignmentTouched, setAssignmentTouched] = useState(editing)

  const groups = useQuery({ queryKey: ['asset-groups', companyId], queryFn: () => assetGroupsApi.list(companyId) })

  const set = <K extends keyof AssetRequest>(key: K, value: AssetRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  /**
   * Picking a type nudges the assignment: a laptop is usually someone's, a firewall usually isn't.
   * It only fires while the user hasn't touched the assignment themselves — a default that keeps
   * overriding a deliberate choice is worse than no default at all.
   */
  const setType = (t: AssetType) =>
    setForm((f) => ({
      ...f,
      type: t,
      assignedTo: assignmentTouched ? f.assignedTo : (PERSONAL.includes(t) ? 'SYSTEM_USER' : 'SHARED'),
    }))

  const setAssignment = (v: AssignmentValue) => {
    setAssignmentTouched(true)
    setForm((f) => ({ ...f, ...v }))
  }

  /**
   * Clearing the warranty end date also disarms the reminder.
   *
   * Without this the checkbox goes disabled while still ticked, and submitting then fails with
   * "turn it on after setting a date" about a toggle that is visibly already on. Same shape as
   * the contract form disarming auto-ticketing when a contract turns ticket-based: a setting that
   * can no longer apply gets switched off rather than left armed behind a greyed-out control.
   */
  const setWarrantyEnd = (v: string) =>
    setForm((f) => ({
      ...f,
      warrantyEnd: v || null,
      warrantyReminderEnabled: v ? f.warrantyReminderEnabled : false,
    }))

  const save = useMutation({
    mutationFn: (body: AssetRequest) => (editing ? assetsApi.update(asset.id, body) : assetsApi.create(body)),
    onSuccess: (saved) => {
      void qc.invalidateQueries({ queryKey: ['assets'] })
      void qc.invalidateQueries({ queryKey: ['asset-groups'] })
      toast(editing ? 'Asset updated' : 'Asset added')
      onSaved(saved)
    },
    onError: (e) => {
      setError(errorMessage(e))
      setFieldErrors(isApiError(e) ? (e.fields ?? {}) : {})
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setFieldErrors({})

    if (!form.name.trim()) { setError('Give the asset a name'); return }
    if (form.assignedTo === 'SYSTEM_USER' && !form.ownerId) { setError('Pick the user this asset is assigned to'); return }
    if (form.assignedTo === 'GUEST' && !form.guestName?.trim()) { setError('Enter the name of the person this asset is assigned to'); return }
    if (form.warrantyStart && form.warrantyEnd && form.warrantyEnd < form.warrantyStart) {
      setError("The warranty end date can't be before the warranty start date"); return
    }
    // Checked here as well as on the server because the toggle is the thing being switched on:
    // "saved, but silently does nothing" is a worse answer than "set an end date first".
    if (form.warrantyReminderEnabled && !form.warrantyEnd) {
      setError('Set a warranty end date before turning the reminder on'); return
    }

    const trim = (s?: string) => (s?.trim() ? s.trim() : undefined)
    save.mutate({
      ...form,
      companyId,
      name: form.name.trim(),
      assetTag: trim(form.assetTag),
      make: trim(form.make),
      model: trim(form.model),
      serialNumber: trim(form.serialNumber),
      location: trim(form.location),
      notes: trim(form.notes),
      guestName: trim(form.guestName),
      guestEmail: trim(form.guestEmail),
      purchaseDate: form.purchaseDate || null,
      warrantyStart: form.warrantyStart || null,
      warrantyEnd: form.warrantyEnd || null,
      warrantyReminderDays: form.warrantyReminderEnabled ? (form.warrantyReminderDays ?? 30) : null,
    })
  }

  return (
    <Modal
      open
      onClose={onClose}
      size="xl"
      title={editing ? `Edit "${asset.name}"` : 'Add an asset'}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="asset-form" loading={save.isPending}>{editing ? 'Save changes' : 'Add asset'}</Button>
        </div>
      }
    >
      <form id="asset-form" onSubmit={submit} className="space-y-5">
        {error && <Alert>{error}</Alert>}

        {companyName && (
          <p className="text-sm text-muted">
            Adding to <span className="font-medium text-ink">{companyName}</span>
          </p>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Name" error={fieldErrors.name}>
            <Input value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="HQ Core Switch" invalid={!!fieldErrors.name} />
          </Field>
          <Field label="Asset tag (optional)" hint="Your reference. Must be unique within this company." error={fieldErrors.assetTag}>
            <Input value={form.assetTag ?? ''} onChange={(e) => set('assetTag', e.target.value)} placeholder="MTS-SW-0042" invalid={!!fieldErrors.assetTag} />
          </Field>
          <Field label="Type">
            <SelectMenu value={form.type} onChange={(v) => setType(v as AssetType)} options={ASSET_TYPES} />
          </Field>
          <Field label="Status">
            <SelectMenu
              value={form.status}
              onChange={(v) => set('status', v as AssetStatus)}
              options={ASSET_STATUSES}
            />
          </Field>
          <Field label="Make (optional)">
            <Input value={form.make ?? ''} onChange={(e) => set('make', e.target.value)} placeholder="Fortinet" />
          </Field>
          <Field label="Model (optional)">
            <Input value={form.model ?? ''} onChange={(e) => set('model', e.target.value)} placeholder="FortiGate 100F" />
          </Field>
          <Field label="Serial number (optional)">
            <Input value={form.serialNumber ?? ''} onChange={(e) => set('serialNumber', e.target.value)} placeholder="FG100F0000012345" />
          </Field>
          <Field label="Location (optional)">
            <Input value={form.location ?? ''} onChange={(e) => set('location', e.target.value)} placeholder="DC Rack B3" />
          </Field>
          <Field
            label="Asset group (optional)"
            hint={groups.data?.length === 0 ? 'This company has no groups yet — create one under the Asset groups tab.' : 'A contract that covers this group covers this asset.'}
            error={fieldErrors.groupId}
            className="sm:col-span-2"
          >
            <SelectMenu
              value={form.groupId ?? -1}
              onChange={(v) => set('groupId', v === -1 ? null : (v as number))}
              disabled={groups.isLoading || !groups.data?.length}
              placeholder={groups.isLoading ? 'Loading groups…' : 'No group'}
              options={[
                { value: -1, label: 'No group' },
                ...(groups.data ?? []).map((g) => ({ value: g.id, label: g.name, description: g.description ?? undefined })),
              ]}
            />
          </Field>
        </div>

        <AssetAssignmentField
          companyId={companyId}
          value={{
            assignedTo: form.assignedTo,
            ownerId: form.ownerId ?? null,
            guestName: form.guestName ?? '',
            guestEmail: form.guestEmail ?? '',
          }}
          onChange={setAssignment}
          error={fieldErrors.ownerId ?? fieldErrors.guestName}
        />

        <div>
          <p className="mb-3 text-sm font-semibold">Dates and warranty</p>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Purchased (optional)">
              <Input type="date" value={form.purchaseDate ?? ''} onChange={(e) => set('purchaseDate', e.target.value || null)} />
            </Field>
            <Field label="Warranty starts (optional)">
              <Input type="date" value={form.warrantyStart ?? ''} onChange={(e) => set('warrantyStart', e.target.value || null)} />
            </Field>
            <Field label="Warranty ends (optional)">
              <Input type="date" value={form.warrantyEnd ?? ''} onChange={(e) => setWarrantyEnd(e.target.value)} />
            </Field>
          </div>

          <div className="mt-4 rounded-lg border border-line p-3">
            <Checkbox
              label="Email a reminder before the warranty ends"
              checked={form.warrantyReminderEnabled}
              disabled={!form.warrantyEnd}
              onChange={(e) => set('warrantyReminderEnabled', e.target.checked)}
            />
            <p className="mt-1 pl-6 text-xs text-muted">
              {form.warrantyEnd
                ? "Goes to this company's administrators, and to their main engineer."
                : 'Set a warranty end date above to enable this.'}
            </p>
            {form.warrantyReminderEnabled && (
              <div className="mt-3 flex items-center gap-2 pl-6">
                <Input
                  type="number"
                  min={1}
                  max={365}
                  className="w-24"
                  aria-label="Days before expiry"
                  value={form.warrantyReminderDays ?? 30}
                  onChange={(e) => set('warrantyReminderDays', Number(e.target.value) || 30)}
                />
                <span className="text-sm text-muted">days before it expires</span>
              </div>
            )}
          </div>
        </div>

        <Field label="Notes (optional)">
          <Textarea rows={2} value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value)} placeholder="Replaces the old SG300. Config backed up to the NAS." />
        </Field>
      </form>
    </Modal>
  )
}
