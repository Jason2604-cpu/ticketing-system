import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { Checkbox, Field, Input, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { MultiSelectMenu } from '../../components/ui/MultiSelectMenu'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { contractsApi } from '../../api/contracts'
import { companiesApi } from '../../api/companies'
import { ticketsApi } from '../../api/tickets'
import { assetsApi } from '../../api/assets'
import { assetGroupsApi } from '../../api/assetGroups'
import { assetOption, groupOption } from '../assets/assetOptions'
import { errorMessage, isApiError } from '../../lib/api'
import { addMonthsIso, formatDateOnlyShort, todayIso } from '../../lib/format'
import type { ContractFrequency, ContractRequest, ContractStatus, ContractType, ServiceContract, TicketCategory, TicketPriority } from '../../types'
import { CATEGORIES, CONTRACT_TYPES, FREQUENCIES, PRIORITIES, defaultOccurrences, frequencyMonths } from './contractMeta'

const blank = (): ContractRequest => {
  const start = todayIso()
  return {
    companyId: 0,
    name: '',
    contractNumber: '',
    description: '',
    status: 'ACTIVE',
    contractType: 'SCHEDULED_VISITS',
    startDate: start,
    endDate: addMonthsIso(start, 12),
    frequency: 'QUARTERLY',
    occurrences: 4,
    firstVisitDate: start,
    coveredAssets: '',
    assetIds: [],
    assetGroupIds: [],
    ownerId: null,
    renewalReminderDays: 30,
    autoCreateTicket: false,
    ticketLeadDays: 7,
    ticketPriority: 'MEDIUM',
    ticketCategory: 'TECHNICAL',
  }
}

const fromContract = (c: ServiceContract): ContractRequest => ({
  companyId: c.companyId,
  name: c.name,
  contractNumber: c.contractNumber ?? '',
  description: c.description ?? '',
  status: c.status,
  contractType: c.contractType,
  startDate: c.startDate,
  endDate: c.endDate,
  frequency: c.frequency,
  occurrences: c.occurrences,
  firstVisitDate: c.firstVisitDate,
  coveredAssets: c.coveredAssets ?? '',
  assetIds: c.coveredAssetItems?.map((a) => a.id) ?? [],
  assetGroupIds: c.coveredGroups?.map((g) => g.id) ?? [],
  ownerId: c.ownerId ?? null,
  renewalReminderDays: c.renewalReminderDays ?? null,
  autoCreateTicket: c.autoCreateTicket,
  ticketLeadDays: c.ticketLeadDays,
  ticketPriority: c.ticketPriority,
  ticketCategory: c.ticketCategory,
  regenerateSchedule: false,
})

/**
 * Create/edit a service contract.
 *
 * The schedule preview below the dates is the point of this form: the generated visit dates are
 * the thing people get wrong, and showing them before saving beats saving and reading back.
 * It mirrors the backend's ContractScheduleGenerator exactly — same month arithmetic, same
 * "drop occurrences past the end date" rule.
 */
export function ContractFormModal({ contract, onClose, onSaved }: {
  contract: ServiceContract | null
  onClose: () => void
  onSaved: (saved: ServiceContract) => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const editing = contract !== null
  const [form, setForm] = useState<ContractRequest>(contract ? fromContract(contract) : blank())
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list })
  const staff = useQuery({ queryKey: ['tickets', 'assignees'], queryFn: ticketsApi.assignees })

  // Coverage pickers are per company — only that company's assets and groups can be covered.
  const hasCompany = form.companyId > 0
  const companyAssets = useQuery({
    queryKey: ['assets', 'list', form.companyId, 'all'],
    queryFn: () => assetsApi.list({ companyId: form.companyId }),
    enabled: hasCompany,
  })
  const companyGroups = useQuery({
    queryKey: ['asset-groups', form.companyId],
    queryFn: () => assetGroupsApi.list(form.companyId),
    enabled: hasCompany,
  })

  /** Changing the company drops the coverage picks — they belonged to the old company. */
  const setCompany = (id: number) =>
    setForm((f) => (f.companyId === id ? f : { ...f, companyId: id, assetIds: [], assetGroupIds: [] }))

  /**
   * Individual picks are for UNGROUPED kit only — a grouped asset is covered by picking its group.
   * Exception: an asset already selected (an older contract, or one grouped after it was picked)
   * stays listed so it can still be seen and removed.
   */
  const individualOptions = useMemo(
    () => (companyAssets.data ?? [])
      .filter((a) => !a.groupId || form.assetIds.includes(a.id))
      .map((a) => {
        const o = assetOption(a)
        return a.groupId ? { ...o, description: [o.description, `In group ${a.groupName}`].filter(Boolean).join(' · ') } : o
      }),
    [companyAssets.data, form.assetIds],
  )
  const groupedCount = (companyAssets.data ?? []).filter((a) => a.groupId).length

  /** Distinct assets covered: individual picks plus every member of every picked group, once each. */
  const coveredTotal = useMemo(() => {
    const ids = new Set(form.assetIds)
    for (const g of companyGroups.data ?? []) {
      if (form.assetGroupIds.includes(g.id)) g.assets.forEach((a) => ids.add(a.id))
    }
    return ids.size
  }, [form.assetIds, form.assetGroupIds, companyGroups.data])

  const set = <K extends keyof ContractRequest>(key: K, value: ContractRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  const scheduled = form.contractType === 'SCHEDULED_VISITS'

  /**
   * Switching to ticket-based also disarms auto-ticketing. Leaving it ticked but hidden would mean
   * a setting the user can't see is still on — and on a contract with no visits it has nothing to
   * fire from anyway, so the backend clears it too.
   */
  const setContractType = (t: ContractType) =>
    setForm((f) => ({ ...f, contractType: t, autoCreateTicket: t === 'SCHEDULED_VISITS' && f.autoCreateTicket }))

  /** Changing the frequency resets the occurrence count to a full year of it — the usual intent. */
  const setFrequency = (f: ContractFrequency) =>
    setForm((prev) => ({ ...prev, frequency: f, occurrences: defaultOccurrences(f) }))

  // Same rules as ContractScheduleGenerator: month arithmetic, and stop once past the end date.
  const preview = useMemo(() => {
    const first = form.firstVisitDate || form.startDate
    const months = frequencyMonths(form.frequency)
    const wanted = form.frequency === 'ONE_OFF' ? 1 : Math.max(1, Math.min(form.occurrences || 1, 120))
    const out: string[] = []
    for (let i = 0; i < wanted; i++) {
      const d = months === 0 ? first : addMonthsIso(first, months * i)
      if (i > 0 && form.endDate && d > form.endDate) break
      out.push(d)
    }
    return out
  }, [form.firstVisitDate, form.startDate, form.endDate, form.frequency, form.occurrences])

  const dropped = (form.frequency === 'ONE_OFF' ? 1 : form.occurrences) - preview.length

  const save = useMutation({
    mutationFn: (body: ContractRequest) => (editing ? contractsApi.update(contract.id, body) : contractsApi.create(body)),
    onSuccess: (saved) => {
      void qc.invalidateQueries({ queryKey: ['contracts'] })
      toast(editing ? 'Contract updated' : 'Contract created')
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
    if (!form.companyId) { setError('Pick the company this contract is with'); return }
    if (!form.name.trim()) { setError('Give the contract a name'); return }
    if (form.endDate < form.startDate) { setError("The end date can't be before the start date"); return }
    save.mutate({
      ...form,
      contractNumber: form.contractNumber?.trim() || undefined,
      description: form.description?.trim() || undefined,
      coveredAssets: form.coveredAssets?.trim() || undefined,
      renewalReminderDays: form.renewalReminderDays ?? null,
    })
  }

  return (
    <Modal
      open
      onClose={onClose}
      size="xl"
      title={editing ? `Edit "${contract.name}"` : 'New service contract'}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="contract-form" loading={save.isPending}>{editing ? 'Save changes' : 'Create contract'}</Button>
        </div>
      }
    >
      <form id="contract-form" onSubmit={submit} className="space-y-5">
        {error && <Alert>{error}</Alert>}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Company" error={fieldErrors.companyId}>
            <SelectMenu
              value={form.companyId || ''}
              onChange={(v) => setCompany(v as number)}
              placeholder="Pick a company"
              options={(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
              invalid={!!fieldErrors.companyId}
            />
          </Field>
          <Field label="Contract name" error={fieldErrors.name}>
            <Input value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Annual preventive maintenance" invalid={!!fieldErrors.name} />
          </Field>
          <Field label="Reference (optional)" hint="Your own or the customer's contract number">
            <Input value={form.contractNumber ?? ''} onChange={(e) => set('contractNumber', e.target.value)} placeholder="MTS-PM-2026-014" />
          </Field>
          <Field label="Status">
            <SelectMenu
              value={form.status}
              onChange={(v) => set('status', v as ContractStatus)}
              options={[
                { value: 'ACTIVE', label: 'Active', description: 'Counts towards schedules and reminders' },
                { value: 'CANCELLED', label: 'Cancelled', description: 'Kept for the record; no tickets or reminders' },
              ]}
            />
          </Field>
        </div>

        <Field label="What the contract covers (optional)">
          <Textarea rows={2} value={form.description ?? ''} onChange={(e) => set('description', e.target.value)} placeholder="Two engineers on site, full system health check, firmware review, written report within 5 working days." />
        </Field>

        <div>
          <p className="mb-3 text-sm font-semibold">Term and schedule</p>

          <Field label="Contract type" className="mb-4">
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {CONTRACT_TYPES.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  onClick={() => setContractType(o.value)}
                  className={`rounded-lg border p-3 text-left transition-colors ${
                    form.contractType === o.value ? 'border-brand-600 bg-brand-50' : 'border-line bg-surface hover:bg-gray-50'
                  }`}
                >
                  <p className={`text-sm font-medium ${form.contractType === o.value ? 'text-brand-700' : ''}`}>{o.label}</p>
                  <p className="mt-0.5 text-xs text-muted">{o.description}</p>
                </button>
              ))}
            </div>
          </Field>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Start date">
              <Input type="date" value={form.startDate} onChange={(e) => set('startDate', e.target.value)} />
            </Field>
            <Field label="End date">
              <Input type="date" value={form.endDate} onChange={(e) => set('endDate', e.target.value)} />
            </Field>
            {scheduled && (
              <Field label="First scheduled visit" hint="Defaults to the start date">
                <Input type="date" value={form.firstVisitDate ?? form.startDate} onChange={(e) => set('firstVisitDate', e.target.value)} />
              </Field>
            )}
            {scheduled && (
              <Field label="Frequency">
                <SelectMenu value={form.frequency} onChange={(v) => setFrequency(v as ContractFrequency)} options={FREQUENCIES.map((f) => ({ value: f.value, label: f.label }))} />
              </Field>
            )}
            {scheduled && (
              <Field label="Number of visits" hint={form.frequency === 'ONE_OFF' ? 'A one-off contract has exactly one' : undefined}>
                <Input
                  type="number"
                  min={1}
                  max={120}
                  disabled={form.frequency === 'ONE_OFF'}
                  value={form.frequency === 'ONE_OFF' ? 1 : form.occurrences}
                  onChange={(e) => set('occurrences', Math.max(1, Number(e.target.value) || 1))}
                />
              </Field>
            )}
          </div>

          {!scheduled && (
            <p className="mt-3 rounded-lg border border-line bg-gray-50/60 p-3 text-xs text-muted">
              No visits are scheduled for a ticket-based contract — the term, owner and renewal reminder below
              still apply, and the customer raises tickets as normal through their subscription.
              {editing && ' Saving this will clear any visits still outstanding on the existing schedule; completed and already-ticketed ones are kept.'}
            </p>
          )}

          {scheduled && <div className="mt-3 rounded-lg border border-line bg-gray-50/60 p-3">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">
              Schedule preview — {preview.length} visit{preview.length === 1 ? '' : 's'}
            </p>
            <div className="flex flex-wrap gap-1.5">
              {preview.map((d, i) => (
                <span key={d + i} className="rounded-md bg-surface px-2 py-1 text-xs ring-1 ring-line">
                  <span className="text-muted">{i + 1}.</span> {formatDateOnlyShort(d)} <span className="text-muted">{d.slice(0, 4)}</span>
                </span>
              ))}
            </div>
            {dropped > 0 && (
              <p className="mt-2 text-xs text-amber-700">
                {dropped} visit{dropped === 1 ? '' : 's'} would fall past the end date and {dropped === 1 ? 'was' : 'were'} dropped —
                extend the term or lower the count.
              </p>
            )}
            {editing && (
              <Checkbox
                className="mt-3 items-start text-xs"
                checked={!!form.regenerateSchedule}
                onChange={(e) => set('regenerateSchedule', e.target.checked)}
                label={
                  <span className="text-muted">
                    Rebuild the schedule from these dates. Visits already completed, skipped, or with a ticket raised are kept;
                    the remaining scheduled ones are replaced, losing any notes or rescheduled dates on them.
                  </span>
                }
              />
            )}
          </div>}
        </div>

        <div>
          <div className="mb-3 flex items-baseline justify-between gap-2">
            <p className="text-sm font-semibold">Covered equipment</p>
            {hasCompany && (form.assetIds.length > 0 || form.assetGroupIds.length > 0) && (
              <p className="text-xs text-muted">Covers {coveredTotal} asset{coveredTotal === 1 ? '' : 's'} in total</p>
            )}
          </div>

          {!hasCompany ? (
            <p className="rounded-lg border border-line bg-gray-50/60 p-3 text-xs text-muted">
              Pick the company first — you can then choose from its asset groups and registered assets.
            </p>
          ) : companyAssets.isError || companyGroups.isError ? (
            <Alert>{errorMessage(companyAssets.error ?? companyGroups.error)}</Alert>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Asset groups" hint="Covers everything in the group, including assets added to it later">
                <MultiSelectMenu
                  values={form.assetGroupIds}
                  onChange={(v) => set('assetGroupIds', v)}
                  options={(companyGroups.data ?? []).map(groupOption)}
                  emptyLabel={companyGroups.isLoading ? 'Loading groups…' : companyGroups.data?.length ? 'No groups selected' : 'This company has no asset groups'}
                  disabled={companyGroups.isLoading || !companyGroups.data?.length}
                  searchPlaceholder="Search groups…"
                />
              </Field>
              <Field
                label="Individual assets"
                hint={groupedCount > 0
                  ? `Assets that aren't in a group. ${groupedCount} grouped asset${groupedCount === 1 ? ' is' : 's are'} covered by picking ${groupedCount === 1 ? 'its' : 'their'} group.`
                  : "Assets that aren't in a group"}
              >
                <MultiSelectMenu
                  values={form.assetIds}
                  onChange={(v) => set('assetIds', v)}
                  options={individualOptions}
                  emptyLabel={companyAssets.isLoading ? 'Loading assets…'
                    : individualOptions.length ? 'No assets selected'
                    : companyAssets.data?.length ? 'Every asset is in a group — pick the group instead'
                    : 'This company has no assets registered'}
                  disabled={companyAssets.isLoading || !individualOptions.length}
                  searchPlaceholder="Search by name or tag…"
                />
              </Field>
            </div>
          )}

          <Field label="Other equipment or notes (optional)" hint="Anything not on the asset register — exclusions, site notes, kit to be registered later" className="mt-4">
            <Textarea rows={2} value={form.coveredAssets ?? ''} onChange={(e) => set('coveredAssets', e.target.value)} placeholder="Excludes UPS batteries. Two spare switches in storage, not yet registered." />
          </Field>
        </div>

        <div>
          <p className="mb-3 text-sm font-semibold">Renewal</p>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Renewal owner" hint="Who chases the renewal. Admins and engineers only.">
              <SelectMenu
                value={form.ownerId ?? ''}
                onChange={(v) => set('ownerId', v === -1 ? null : (v as number))}
                placeholder="Nobody yet"
                options={[{ value: -1, label: 'Nobody yet' }, ...(staff.data ?? []).map((u) => ({ value: u.id, label: `${u.firstName} ${u.lastName}`, description: u.role === 'ADMIN' ? 'Admin' : 'Engineer' }))]}
              />
            </Field>
            <Field label="Remind this many days before expiry" hint="Blank or 0 turns the reminder off">
              <Input
                type="number"
                min={0}
                max={365}
                value={form.renewalReminderDays ?? ''}
                onChange={(e) => set('renewalReminderDays', e.target.value === '' ? null : Math.max(0, Number(e.target.value) || 0))}
              />
            </Field>
          </div>
        </div>

        {scheduled && <div>
          <p className="mb-3 text-sm font-semibold">Automatic tickets</p>
          <Checkbox
            className="items-start"
            checked={form.autoCreateTicket}
            onChange={(e) => set('autoCreateTicket', e.target.checked)}
            label={
              <span className="text-sm">
                Raise a ticket automatically as each visit comes due
                <span className="block text-xs text-muted">
                  The ticket is pre-assigned to the company's main engineer and carries its normal SLA.
                  Leave this off to keep the schedule as a reminder only.
                </span>
              </span>
            }
          />

          {form.autoCreateTicket && (
            <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label="Days ahead" hint="How early the ticket appears">
                <Input type="number" min={0} max={90} value={form.ticketLeadDays} onChange={(e) => set('ticketLeadDays', Math.max(0, Number(e.target.value) || 0))} />
              </Field>
              <Field label="Priority">
                <SelectMenu value={form.ticketPriority} onChange={(v) => set('ticketPriority', v as TicketPriority)} options={PRIORITIES} />
              </Field>
              <Field label="Category">
                <SelectMenu value={form.ticketCategory} onChange={(v) => set('ticketCategory', v as TicketCategory)} options={CATEGORIES} />
              </Field>
            </div>
          )}
        </div>}
      </form>
    </Modal>
  )
}
