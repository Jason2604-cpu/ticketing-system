import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, CheckCheck, Filter, Plus, RefreshCw, Search, UserPlus, X } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { PriorityBadge, SlaBadge, StatusBadge } from '../../components/ui/Badge'
import { Avatar } from '../../components/ui/Avatar'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { EmptyState } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { NewTicketModal } from './NewTicketModal'
import { ticketsApi, type TicketFilter } from '../../api/tickets'
import { companiesApi } from '../../api/companies'
import { useAuth } from '../../auth/useAuth'
import { api, errorMessage } from '../../lib/api'
import { formatShortDate, shortName } from '../../lib/format'
import type { SlaState, TicketPriority, TicketStatus, TicketSummary } from '../../types'

const STATUS_OPTS = [{ value: '', label: 'All Statuses' }, { value: 'OPEN', label: 'Open' }, { value: 'IN_PROGRESS', label: 'In Progress' }, { value: 'ON_HOLD', label: 'On Hold' }, { value: 'RESOLVED', label: 'Resolved' }, { value: 'CLOSED', label: 'Closed' }] as const
/**
 * The one SLA badge a row shows (quiet: nothing when all is well). A clock in trouble wins —
 * resolution first, then first response — and a paused resolution clock shows as paused.
 */
function rowSla(t: TicketSummary): SlaState | undefined {
  if (t.resolutionSla === 'BREACHED' || t.resolutionSla === 'AT_RISK') return t.resolutionSla
  if (t.responseSla === 'BREACHED' || t.responseSla === 'AT_RISK') return t.responseSla
  return t.resolutionSla === 'PAUSED' ? 'PAUSED' : t.responseSla
}

const PRIORITY_OPTS = [{ value: '', label: 'All Priorities' }, { value: 'CRITICAL', label: 'Critical' }, { value: 'HIGH', label: 'High' }, { value: 'MEDIUM', label: 'Medium' }, { value: 'LOW', label: 'Low' }] as const

export default function TicketsPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { hasRole, user } = useAuth()
  const isStaff = hasRole('ADMIN', 'ENGINEER')

  const urlQ = params.get('q') ?? ''
  const [qState, setQ] = useState<{ base: string; value: string }>({ base: urlQ, value: urlQ })
  const q = qState.base === urlQ ? qState.value : urlQ
  const status = (params.get('status') ?? '') as TicketStatus | ''
  const priority = (params.get('priority') ?? '') as TicketPriority | ''
  const companyId = params.get('companyId') ? Number(params.get('companyId')) : ''
  // Set by the dashboard's "waiting for a first response" banner. No dropdown for these two, so
  // they show as removable chips next to the filters.
  const awaiting = params.get('awaiting') === 'first-response'
  const assigneeId = params.get('assigneeId') ? Number(params.get('assigneeId')) : ''
  const [newOpen, setNewOpen] = useState(false)
  const [selected, setSelected] = useState<Set<number>>(new Set())

  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v); else next.delete(k)
    setParams(next, { replace: true })
  }

  const filter: TicketFilter = { q, status, priority, companyId, assigneeId, awaitingResponse: awaiting || undefined }
  const hasFilter = Boolean(q || status || priority || companyId || assigneeId || awaiting)
  // Unfiltered, the list IS "all tickets": share one cache entry (one request per poll instead of
  // two identical ones). The "of N" total only needs its own request while a filter is applied,
  // and it doesn't have to poll.
  const all = useQuery({ queryKey: ['tickets', 'all'], queryFn: () => ticketsApi.list(), refetchInterval: false })
  const tickets = useQuery({
    queryKey: hasFilter ? ['tickets', filter] : ['tickets', 'all'],
    queryFn: () => ticketsApi.list(hasFilter ? filter : {}),
  })
  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list, enabled: isStaff })
  const assignees = useQuery({ queryKey: ['tickets', 'assignees'], queryFn: ticketsApi.assignees, enabled: isStaff })

  const rows = tickets.data ?? []
  const allSelected = rows.length > 0 && rows.every((t) => selected.has(t.id))
  const someSelected = selected.size > 0
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(rows.map((t) => t.id)))
  const toggleOne = (id: number) => setSelected((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n })

  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['tickets'] }) }
  const bulkStatus = useMutation({
    mutationFn: async (s: TicketStatus) => { for (const id of selected) await ticketsApi.setStatus(id, s) },
    onSuccess: () => { invalidate(); toast(`${selected.size} ticket${selected.size > 1 ? 's' : ''} updated`); setSelected(new Set()) },
    onError: (e) => { invalidate(); toast(errorMessage(e), 'error') },
  })
  const bulkAssign = useMutation({
    mutationFn: async (userId: number) => { for (const id of selected) await ticketsApi.assign(id, userId) },
    onSuccess: () => { invalidate(); toast('Assigned'); setSelected(new Set()) },
    onError: (e) => { invalidate(); toast(errorMessage(e), 'error') },
  })

  // "Awaiting first response" spans several statuses, so picking a status replaces it.
  const setStatusFilter = (v: string) => {
    const next = new URLSearchParams(params)
    next.delete('awaiting')
    if (v) next.set('status', v); else next.delete('status')
    setParams(next, { replace: true })
  }
  const assigneeName = assigneeId === user?.id ? 'you'
    : (() => { const a = assignees.data?.find((u) => u.id === assigneeId); return a ? `${a.firstName} ${a.lastName}` : `#${assigneeId}` })()

  const companyOpts = useMemo(() => [{ value: 0, label: 'All Companies' }, ...(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))], [companies.data])

  return (
    <>
      <Topbar title="All Tickets" />
      <Page>
        <div className="flex items-center gap-3">
          <div className="relative flex-1">
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input
              value={q}
              onChange={(e) => { setQ({ base: urlQ, value: e.target.value }); setParam('q', e.target.value) }}
              placeholder="Search by title or ticket ID…"
              className="h-11 w-full rounded-lg border border-line bg-surface pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
            />
          </div>
          <Button variant="secondary" size="lg" onClick={() => { setParams({}, { replace: true }); setSelected(new Set()) }}><RefreshCw size={16} /> Clear</Button>
          <Button size="lg" onClick={() => setNewOpen(true)}><Plus size={16} /> New</Button>
        </div>

        <div className="mt-3 flex items-center gap-3">
          <Filter size={16} className="text-muted" />
          <SelectMenu className="w-44" size="sm" value={status} onChange={setStatusFilter} options={[...STATUS_OPTS]} />
          <SelectMenu className="w-44" size="sm" value={priority} onChange={(v) => setParam('priority', v)} options={[...PRIORITY_OPTS]} />
          {isStaff && <SelectMenu className="w-52" size="sm" value={companyId || 0} onChange={(v) => setParam('companyId', v ? String(v) : '')} options={companyOpts} />}
          {awaiting && <FilterChip label="Awaiting first response" onRemove={() => setParam('awaiting', '')} />}
          {assigneeId !== '' && <FilterChip label={`Assigned to ${assigneeName}`} onRemove={() => setParam('assigneeId', '')} />}
          <span className="ml-auto whitespace-nowrap text-sm text-muted">{rows.length} of {all.data?.length ?? 0} tickets</span>
        </div>

        {someSelected && (
          <div className="mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-brand-200 bg-brand-50 px-4 py-2.5 text-sm">
            <span className="font-medium text-brand-700">{selected.size} selected</span>
            {isStaff && (
              <>
                <span className="mx-1 text-brand-200">|</span>
                <SelectMenu<number> className="w-52" size="sm" value="" placeholder="Assign to…" onChange={(v) => bulkAssign.mutate(v)}
                  options={(assignees.data ?? []).map((u) => ({ value: u.id, label: `${u.firstName} ${u.lastName}`, icon: <UserPlus size={14} /> }))} />
                <Button size="sm" variant="secondary" onClick={() => bulkStatus.mutate('IN_PROGRESS')} loading={bulkStatus.isPending}>Mark In Progress</Button>
                <Button size="sm" variant="secondary" onClick={() => bulkStatus.mutate('RESOLVED')} loading={bulkStatus.isPending}><CheckCheck size={14} /> Mark Resolved</Button>
              </>
            )}
            <button className="ml-auto inline-flex items-center gap-1 text-muted hover:text-ink" onClick={() => setSelected(new Set())}><X size={14} /> Clear selection</button>
          </div>
        )}

        <Card className="mt-4 overflow-hidden">
          <Table>
            <thead className="bg-gray-50/60">
              <tr>
                <Th className="w-10"><input type="checkbox" className="h-4 w-4 rounded border-line accent-[var(--accent-600)]" checked={allSelected} ref={(el) => { if (el) el.indeterminate = someSelected && !allSelected }} onChange={toggleAll} aria-label="Select all" /></Th>
                <Th>#ID</Th><Th>Title</Th><Th>Company</Th><Th>Priority</Th><Th>Status</Th><Th>SLA</Th><Th>Assignee</Th><Th>Created</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((t) => (
                <Tr key={t.id} onClick={() => navigate(`/tickets/${t.id}`)} className={selected.has(t.id) ? 'bg-brand-50/50' : undefined}>
                  <Td onClick={(e) => e.stopPropagation()}><input type="checkbox" className="h-4 w-4 rounded border-line accent-[var(--accent-600)]" checked={selected.has(t.id)} onChange={() => toggleOne(t.id)} aria-label={`Select ticket ${t.id}`} /></Td>
                  <Td className="font-mono text-xs text-muted">#{t.id}</Td>
                  <Td className="font-medium">{t.title}</Td>
                  <Td>
                    <Link to={`/companies/${t.companyId}`} onClick={(e) => e.stopPropagation()} className="inline-flex items-center gap-1.5 text-brand-600 hover:underline">
                      <Building2 size={14} /> {t.companyName}
                    </Link>
                  </Td>
                  <Td><PriorityBadge priority={t.priority} /></Td>
                  <Td><span title={t.status === 'ON_HOLD' ? t.holdReasonLabel : undefined}><StatusBadge status={t.status} /></span></Td>
                  {/* Quiet mode: only shows a badge when the ticket needs attention (or is paused). */}
                  <Td><SlaBadge state={rowSla(t)} quiet /></Td>
                  <Td>
                    {t.assignee ? (
                      <span className="inline-flex items-center gap-2"><Avatar size="sm" firstName={t.assignee.firstName} lastName={t.assignee.lastName} src={api.mediaUrl(t.assignee.photoUrl)} /> {shortName(t.assignee.firstName, t.assignee.lastName)}</span>
                    ) : <span className="italic text-gray-400">Unassigned</span>}
                  </Td>
                  <Td className="text-muted">{formatShortDate(t.createdAt)}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          {!tickets.isLoading && rows.length === 0 && (
            <EmptyState title={all.data?.length ? 'No tickets match' : 'No tickets yet'} hint={all.data?.length ? 'Try clearing a filter or searching for something else.' : 'Create the first ticket for a company.'} action={<Button onClick={() => setNewOpen(true)}><Plus size={16} /> New ticket</Button>} />
          )}
        </Card>
      </Page>
      <NewTicketModal open={newOpen} onClose={() => setNewOpen(false)} />
    </>
  )
}

function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1 whitespace-nowrap rounded-full border border-brand-200 bg-brand-50 py-1 pl-3 pr-1.5 text-xs font-medium text-brand-700">
      {label}
      <button type="button" onClick={onRemove} className="rounded-full p-0.5 hover:bg-brand-100" aria-label={`Remove filter: ${label}`}><X size={12} /></button>
    </span>
  )
}
