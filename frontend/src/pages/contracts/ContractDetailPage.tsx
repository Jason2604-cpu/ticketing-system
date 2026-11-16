import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, Ban, Boxes, Building2, HardDrive, CalendarClock, CheckCircle2, FileSignature, FileText,
  Paperclip, Pencil, RefreshCw, Ticket as TicketIcon, Trash2, User as UserIcon, Zap,
} from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { GenericBadge } from '../../components/ui/Badge'
import { Alert, EmptyState, ProgressBar } from '../../components/ui/Misc'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { useToast } from '../../components/ui/useToast'
import { contractsApi } from '../../api/contracts'
import { api, errorMessage } from '../../lib/api'
import { formatBytes, formatDateOnly } from '../../lib/format'
import type { AssetSummary, ContractVisit, ServiceContract, VisitStatus } from '../../types'
import { ContractFormModal } from './ContractFormModal'
import { STATE_LABEL, STATE_TONE, VISIT_LABEL, expiryPhrase } from './contractMeta'

/**
 * One contract: its terms, the generated schedule, and the documents attached to it.
 *
 * Every mutation here returns the whole refreshed contract, so the page is driven from a single
 * query rather than patching rows in place — the derived fields (state, next visit, overdue
 * count) are all computed server-side and would drift if the client tried to keep them in sync.
 */
export default function ContractDetailPage() {
  const { id } = useParams()
  const contractId = Number(id)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  const contract = useQuery({
    queryKey: ['contracts', contractId],
    queryFn: () => contractsApi.get(contractId),
    enabled: Number.isFinite(contractId),
  })

  const refresh = (saved: ServiceContract) => {
    qc.setQueryData(['contracts', contractId], saved)
    void qc.invalidateQueries({ queryKey: ['contracts'] })
  }

  const setVisit = useMutation({
    mutationFn: ({ visitId, status, scheduledDate, notes }: { visitId: number; status: VisitStatus; scheduledDate?: string; notes?: string }) =>
      contractsApi.updateVisit(contractId, visitId, { status, scheduledDate, notes }),
    onSuccess: refresh,
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const regenerate = useMutation({
    mutationFn: () => contractsApi.regenerate(contractId),
    onSuccess: (saved) => { refresh(saved); toast('Schedule rebuilt') },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const upload = useMutation({
    mutationFn: (file: File) => contractsApi.addDocument(contractId, file),
    onSuccess: (saved) => { refresh(saved); toast('Document attached') },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const removeDoc = useMutation({
    mutationFn: (mediaId: string) => contractsApi.removeDocument(contractId, mediaId),
    onSuccess: (saved) => { refresh(saved); toast('Document removed') },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const remove = useMutation({
    mutationFn: () => contractsApi.remove(contractId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['contracts'] })
      toast('Contract deleted')
      navigate('/admin/contracts')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  if (contract.isLoading) return <><Topbar title="Service contract" search={false} newTicket={false} /><Page><Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card></Page></>
  if (contract.isError || !contract.data) return <><Topbar title="Service contract" search={false} newTicket={false} /><Page><Alert>Couldn't load this contract. It may have been deleted.</Alert></Page></>

  const c = contract.data
  const visits = c.visits ?? []
  const docs = c.attachments ?? []
  const scheduled = c.contractType === 'SCHEDULED_VISITS' 

  return (
    <>
      <Topbar
        title={c.name}
        subtitle={`${c.companyName}${c.contractNumber ? ` · ${c.contractNumber}` : ''}`}
        search={false}
        newTicket={false}
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setEditing(true)}><Pencil size={15} /> Edit</Button>
            <Button
              variant="danger"
              loading={remove.isPending}
              onClick={() => confirm(`Delete "${c.name}"? Its schedule and attached documents go with it. Tickets already raised are kept.`) && remove.mutate()}
            >
              <Trash2 size={15} /> Delete
            </Button>
          </div>
        }
      />
      <Page>
        <Link to="/admin/contracts" className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:underline">
          <ArrowLeft size={15} /> All contracts
        </Link>

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
          <div className="space-y-5 lg:col-span-2">
            {/* No schedule card at all for a ticket-based contract — an empty visit table with a
                "Rebuild" button would invite someone to generate visits that shouldn't exist. */}
            {scheduled && <Card>
              <CardHeader
                title={<span className="flex items-center gap-2"><CalendarClock size={16} /> Schedule</span>}
                action={
                  <Button size="sm" variant="secondary" loading={regenerate.isPending}
                    onClick={() => confirm('Rebuild the schedule from the contract dates? Completed, skipped and already-ticketed visits are kept; the rest are replaced.') && regenerate.mutate()}>
                    <RefreshCw size={14} /> Rebuild
                  </Button>
                }
              />
              <CardBody className="pt-0">
                <div className="mb-4 flex items-center gap-3">
                  <ProgressBar value={c.visitsCompleted} max={Math.max(c.visitsTotal, 1)} neutral className="flex-1" />
                  <span className="whitespace-nowrap text-sm text-muted">
                    {c.visitsCompleted} of {c.visitsTotal} done
                    {c.visitsOverdue > 0 && <span className="ml-2 font-medium text-red-600">· {c.visitsOverdue} overdue</span>}
                  </span>
                </div>

                {visits.length === 0 ? (
                  <EmptyState title="No visits scheduled" hint="Rebuild the schedule to generate them from the contract dates." />
                ) : (
                  <div className="-mx-5 overflow-x-auto">
                    <Table>
                      <thead className="bg-gray-50/60">
                        <tr><Th>#</Th><Th>Date</Th><Th>Status</Th><Th>Ticket</Th><Th>Signed off</Th><Th></Th></tr>
                      </thead>
                      <tbody>
                        {visits.map((v) => (
                          <VisitRow
                            key={v.id}
                            v={v}
                            busy={setVisit.isPending}
                            onSet={(status, scheduledDate) => setVisit.mutate({ visitId: v.id, status, scheduledDate })}
                          />
                        ))}
                      </tbody>
                    </Table>
                  </div>
                )}
              </CardBody>
            </Card>}

            <Card>
              <CardHeader
                title={<span className="flex items-center gap-2"><Paperclip size={16} /> Documents</span>}
                action={
                  <>
                    <input
                      ref={fileInput}
                      type="file"
                      className="hidden"
                      accept=".pdf,.png,.jpg,.jpeg,.webp,.csv,.txt"
                      onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); e.target.value = '' }}
                    />
                    <Button size="sm" variant="secondary" loading={upload.isPending} onClick={() => fileInput.current?.click()}>
                      <Paperclip size={14} /> Attach
                    </Button>
                  </>
                }
              />
              <CardBody className="pt-0">
                {docs.length === 0 ? (
                  <p className="py-4 text-center text-sm text-muted">No documents attached. The signed contract, quotation or scope goes here.</p>
                ) : (
                  <ul className="divide-y divide-line">
                    {docs.map((d) => (
                      <li key={d.id} className="flex items-center gap-3 py-2.5">
                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gray-100 text-muted"><FileText size={15} /></span>
                        <div className="min-w-0 flex-1">
                          <a href={api.mediaUrl(d.url)} target="_blank" rel="noreferrer" className="block truncate text-sm font-medium text-brand-600 hover:underline">{d.name}</a>
                          <p className="text-xs text-muted">{formatBytes(d.sizeBytes)}</p>
                        </div>
                        <button
                          className="rounded-md p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
                          aria-label={`Remove ${d.name}`}
                          onClick={() => confirm(`Remove "${d.name}"?`) && removeDoc.mutate(d.id)}
                        >
                          <Trash2 size={15} />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>
          </div>

          <div className="space-y-5">
            <Card>
              <CardHeader title={<span className="flex items-center gap-2"><FileSignature size={16} /> Contract</span>} />
              <CardBody className="space-y-3 pt-0 text-sm">
                <Row label="Status"><GenericBadge tone={STATE_TONE[c.state]}>{STATE_LABEL[c.state]}</GenericBadge></Row>
                <Row label="Company">
                  <Link to={`/companies/${c.companyId}`} className="inline-flex items-center gap-1.5 font-medium text-brand-600 hover:underline">
                    <Building2 size={14} /> {c.companyName}
                  </Link>
                </Row>
                <Row label="Term">{formatDateOnly(c.startDate)} – {formatDateOnly(c.endDate)}</Row>
                <Row label="Expires">
                  <span className={c.daysToExpiry < 0 ? 'font-medium text-red-600' : c.state === 'EXPIRING_SOON' ? 'font-medium text-brand-600' : ''}>
                    {expiryPhrase(c.daysToExpiry)}
                  </span>
                </Row>
                <Row label="Type">{c.contractTypeLabel}</Row>
                {scheduled && <Row label="Frequency">{c.frequencyLabel}{c.frequency !== 'ONE_OFF' && ` · ${c.occurrences} visits`}</Row>}
                {scheduled && <Row label="Next visit">{c.nextVisitDate ? formatDateOnly(c.nextVisitDate) : <span className="italic text-gray-400">All done</span>}</Row>}
                {c.description && (
                  <div className="border-t border-line pt-3">
                    <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">Scope</p>
                    <p className="whitespace-pre-wrap text-sm">{c.description}</p>
                  </div>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title={<span className="flex items-center gap-2"><UserIcon size={16} /> Renewal</span>} />
              <CardBody className="space-y-3 pt-0 text-sm">
                <Row label="Owner">{c.ownerName ?? <span className="italic text-gray-400">Nobody assigned</span>}</Row>
                <Row label="Reminder">
                  {c.renewalReminderDays
                    ? `${c.renewalReminderDays} days before expiry`
                    : <span className="italic text-gray-400">Off</span>}
                </Row>
              </CardBody>
            </Card>

            {scheduled && <Card>
              <CardHeader title={<span className="flex items-center gap-2"><Zap size={16} /> Automatic tickets</span>} />
              <CardBody className="space-y-3 pt-0 text-sm">
                {c.autoCreateTicket ? (
                  <>
                    <Row label="Raised">{c.ticketLeadDays === 0 ? 'On the day' : `${c.ticketLeadDays} days ahead`}</Row>
                    <Row label="Priority">{c.ticketPriority}</Row>
                    <Row label="Category">{c.ticketCategory}</Row>
                  </>
                ) : (
                  <p className="text-muted">Off — this schedule is a reminder only. Turn it on from Edit to have each visit raise its own ticket.</p>
                )}
              </CardBody>
            </Card>}

            <CoveredEquipmentCard c={c} />
          </div>
        </div>
      </Page>

      {editing && (
        <ContractFormModal
          contract={c}
          onClose={() => setEditing(false)}
          onSaved={(saved) => { setEditing(false); refresh(saved) }}
        />
      )}
    </>
  )
}

function VisitRow({ v, busy, onSet }: {
  v: ContractVisit
  busy: boolean
  onSet: (status: VisitStatus, scheduledDate?: string) => void
}) {
  const [rescheduling, setRescheduling] = useState(false)
  const tone = v.status === 'COMPLETED' ? 'green' : v.status === 'SKIPPED' ? 'gray' : v.overdue ? 'red' : 'blue'
  const label = v.status === 'SCHEDULED' && v.overdue ? 'Overdue' : VISIT_LABEL[v.status]

  return (
    <Tr>
      <Td><span className="text-muted">{v.sequence}</span></Td>
      <Td>
        {rescheduling ? (
          <Input
            type="date"
            autoFocus
            defaultValue={v.scheduledDate}
            className="h-8 w-40 text-sm"
            onBlur={() => setRescheduling(false)}
            onChange={(e) => { if (e.target.value) { onSet(v.status, e.target.value); setRescheduling(false) } }}
          />
        ) : (
          <button className="text-left hover:underline" onClick={() => setRescheduling(true)} title="Reschedule">
            <span className={v.overdue ? 'font-medium text-red-600' : ''}>{formatDateOnly(v.scheduledDate)}</span>
          </button>
        )}
      </Td>
      <Td><GenericBadge tone={tone}>{label}</GenericBadge></Td>
      <Td>
        {v.ticketId
          ? <Link to={`/tickets/${v.ticketId}`} className="inline-flex items-center gap-1 text-brand-600 hover:underline"><TicketIcon size={13} /> #{v.ticketId}</Link>
          : <span className="text-gray-400">—</span>}
      </Td>
      <Td>
        {v.completedByName
          ? <div><p className="text-sm">{v.completedByName}</p><p className="text-xs text-muted">{v.completedAt ? new Date(v.completedAt).toLocaleDateString() : ''}</p></div>
          : <span className="text-gray-400">—</span>}
      </Td>
      <Td>
        <div className="flex justify-end gap-1">
          {v.status !== 'COMPLETED' && (
            <button disabled={busy} className="rounded-md p-1.5 text-muted hover:bg-green-50 hover:text-green-600 disabled:opacity-50"
              title="Mark completed" onClick={() => onSet('COMPLETED')}><CheckCircle2 size={15} /></button>
          )}
          {v.status !== 'SKIPPED' && (
            <button disabled={busy} className="rounded-md p-1.5 text-muted hover:bg-gray-100 disabled:opacity-50"
              title="Skip this visit" onClick={() => onSet('SKIPPED')}><Ban size={15} /></button>
          )}
          {v.status !== 'SCHEDULED' && (
            <button disabled={busy} className="rounded-md p-1.5 text-muted hover:bg-gray-100 disabled:opacity-50"
              title="Put back on the schedule" onClick={() => onSet('SCHEDULED')}><RefreshCw size={15} /></button>
          )}
        </div>
      </Td>
    </Tr>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="shrink-0 text-muted">{label}</span>
      <span className="text-right">{children}</span>
    </div>
  )
}

/**
 * What the contract covers: groups (with their live membership), individually listed assets,
 * and the free-text notes for anything not on the register.
 */
function CoveredEquipmentCard({ c }: { c: ServiceContract }) {
  const groups = c.coveredGroups ?? []
  const items = c.coveredAssetItems ?? []
  const total = c.coveredAssetTotal ?? 0
  if (!groups.length && !items.length && !c.coveredAssets) {
    return (
      <Card>
        <CardHeader title="Covered equipment" />
        <CardBody className="pt-0">
          <p className="text-sm text-muted">No equipment linked yet. Edit the contract to pick asset groups or individual assets.</p>
        </CardBody>
      </Card>
    )
  }
  return (
    <Card>
      <CardHeader
        title="Covered equipment"
        action={total > 0 ? <span className="text-xs text-muted">{total} asset{total === 1 ? '' : 's'}</span> : undefined}
      />
      <CardBody className="space-y-4 pt-0">
        {groups.map((g) => (
          <div key={g.id}>
            <p className="flex items-center gap-1.5 text-sm font-medium">
              <Boxes size={14} className="text-brand-600" /> {g.name}
              <span className="text-xs font-normal text-muted">· {g.assetCount} asset{g.assetCount === 1 ? '' : 's'}</span>
            </p>
            {g.assets.length > 0
              ? <ul className="mt-1 space-y-0.5 pl-5">{g.assets.map((a) => <AssetLine key={a.id} a={a} />)}</ul>
              : <p className="mt-1 pl-5 text-xs text-muted">This group is currently empty.</p>}
          </div>
        ))}
        {items.length > 0 && (
          <div>
            {groups.length > 0 && <p className="flex items-center gap-1.5 text-sm font-medium"><HardDrive size={14} className="text-brand-600" /> Individual assets</p>}
            <ul className={groups.length > 0 ? 'mt-1 space-y-0.5 pl-5' : 'space-y-0.5'}>
              {items.map((a) => <AssetLine key={a.id} a={a} />)}
            </ul>
          </div>
        )}
        {c.coveredAssets && (
          <div>
            {(groups.length > 0 || items.length > 0) && <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">Notes</p>}
            <p className="whitespace-pre-wrap text-sm">{c.coveredAssets}</p>
          </div>
        )}
      </CardBody>
    </Card>
  )
}

function AssetLine({ a }: { a: AssetSummary }) {
  return (
    <li className="text-sm">
      <Link to={`/admin/assets/${a.id}`} className="text-brand-700 hover:underline">{a.name}</Link>
      <span className="text-xs text-muted">
        {a.assetTag && ` · ${a.assetTag}`}
        {a.serialNumber && ` · SN ${a.serialNumber}`}
      </span>
    </li>
  )
}
