import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CalendarClock, FileSignature, Plus, Search, Zap } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { GenericBadge } from '../../components/ui/Badge'
import { EmptyState, ProgressBar } from '../../components/ui/Misc'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { contractsApi } from '../../api/contracts'
import { companiesApi } from '../../api/companies'
import { formatDateOnly, formatDateOnlyShort } from '../../lib/format'
import type { ContractState, ServiceContract } from '../../types'
import { ContractFormModal } from './ContractFormModal'
import { STATE_LABEL, STATE_TONE, expiryPhrase } from './contractMeta'

const STATES: { value: ContractState; label: string }[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'EXPIRING_SOON', label: 'Expiring soon' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

/**
 * Admin Panel → Service Contracts. Recurring obligations to customers — preventive maintenance
 * schedules, retainers, annual certifications — with the company / state / text filters that
 * make a list of a few hundred usable.
 *
 * Filtering happens server-side (one query per filter change) rather than in the browser, so the
 * page never shows a filtered view of a truncated list.
 */
export default function ContractsPage() {
  const navigate = useNavigate()
  const [companyId, setCompanyId] = useState<number | ''>('')
  const [state, setState] = useState<ContractState | ''>('')
  const [q, setQ] = useState('')
  const [creating, setCreating] = useState(false)

  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list })
  const contracts = useQuery({
    queryKey: ['contracts', { companyId, state, q }],
    queryFn: () => contractsApi.list({
      companyId: companyId === '' ? undefined : companyId,
      state: state === '' ? undefined : state,
      q: q.trim() || undefined,
    }),
  })

  const rows = useMemo(() => contracts.data ?? [], [contracts.data])
  const stats = useMemo(() => ({
    active: rows.filter((c) => c.state === 'ACTIVE' || c.state === 'EXPIRING_SOON').length,
    expiring: rows.filter((c) => c.state === 'EXPIRING_SOON').length,
    overdue: rows.reduce((n, c) => n + (c.contractType === 'SCHEDULED_VISITS' ? c.visitsOverdue : 0), 0),
    automated: rows.filter((c) => c.autoCreateTicket).length,
  }), [rows])

  const filtered = companyId !== '' || state !== '' || q.trim() !== ''

  return (
    <>
      <Topbar
        title="Service Contracts"
        subtitle="Recurring maintenance, retainers and renewals"
        search={false}
        newTicket={false}
        actions={<Button onClick={() => setCreating(true)}><Plus size={16} /> New contract</Button>}
      />
      <Page>
        <div className="mb-5 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Stat icon={<FileSignature size={16} />} label="In force" value={stats.active} />
          <Stat icon={<CalendarClock size={16} />} label="Expiring soon" value={stats.expiring} tone={stats.expiring > 0 ? 'brand' : undefined} />
          <Stat icon={<AlertTriangle size={16} />} label="Overdue visits" value={stats.overdue} tone={stats.overdue > 0 ? 'red' : undefined} />
          <Stat icon={<Zap size={16} />} label="Auto-ticketing on" value={stats.automated} />
        </div>

        <Card className="mb-4 p-3">
          <div className="flex flex-col gap-3 md:flex-row md:items-center">
            <div className="relative flex-1">
              <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
              <Input
                className="pl-9"
                placeholder="Search name, reference, covered equipment…"
                value={q}
                onChange={(e) => setQ(e.target.value)}
              />
            </div>
            <SelectMenu
              className="md:w-56"
              value={companyId}
              onChange={(v) => setCompanyId(v === -1 ? '' : v)}
              placeholder="All companies"
              options={[{ value: -1, label: 'All companies' }, ...(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))]}
            />
            <SelectMenu
              className="md:w-48"
              value={state}
              onChange={(v) => setState(v === 'ALL' ? '' : (v as ContractState))}
              placeholder="Any status"
              options={[{ value: 'ALL', label: 'Any status' }, ...STATES]}
            />
          </div>
        </Card>

        {contracts.isLoading ? (
          <Card><div className="p-10 text-center text-sm text-muted">Loading contracts…</div></Card>
        ) : rows.length === 0 ? (
          <Card>
            <EmptyState
              icon={<FileSignature size={22} />}
              title={filtered ? 'No contracts match those filters' : 'No service contracts yet'}
              hint={filtered
                ? 'Try clearing the company or status filter.'
                : 'Add the recurring work you owe customers — preventive maintenance visits, support retainers, annual certifications — and the schedule is generated for you.'}
              action={filtered
                ? <Button variant="secondary" onClick={() => { setCompanyId(''); setState(''); setQ('') }}>Clear filters</Button>
                : <Button onClick={() => setCreating(true)}><Plus size={16} /> New contract</Button>}
            />
          </Card>
        ) : (
          <Card className="overflow-hidden">
            <Table>
              <thead className="bg-gray-50/60">
                <tr>
                  <Th>Contract</Th><Th>Company</Th><Th>Term</Th><Th>Schedule</Th><Th>Next visit</Th><Th>Progress</Th><Th>Status</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((c) => <ContractRow key={c.id} c={c} onOpen={() => navigate(`/admin/contracts/${c.id}`)} />)}
              </tbody>
            </Table>
          </Card>
        )}
      </Page>

      {creating && (
        <ContractFormModal
          contract={null}
          onClose={() => setCreating(false)}
          onSaved={(saved) => { setCreating(false); navigate(`/admin/contracts/${saved.id}`) }}
        />
      )}
    </>
  )
}

function ContractRow({ c, onOpen }: { c: ServiceContract; onOpen: () => void }) {
  const scheduled = c.contractType === 'SCHEDULED_VISITS'
  return (
    <Tr onClick={onOpen}>
      <Td>
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600"><FileSignature size={15} /></span>
          <div className="min-w-0">
            <p className="truncate font-medium">{c.name}</p>
            <p className="truncate text-xs text-muted">
              {c.contractNumber ?? 'No reference'}
              {c.autoCreateTicket && <span className="ml-2 text-brand-600">· auto-ticket</span>}
            </p>
          </div>
        </div>
      </Td>
      <Td><span className="whitespace-nowrap">{c.companyName}</span></Td>
      <Td>
        <p className="whitespace-nowrap text-sm">{formatDateOnlyShort(c.startDate)} – {formatDateOnlyShort(c.endDate)}</p>
        <p className={`text-xs ${c.daysToExpiry < 0 ? 'text-red-600' : c.state === 'EXPIRING_SOON' ? 'text-brand-600' : 'text-muted'}`}>
          {c.daysToExpiry < 0 ? 'expired ' : 'expires '}{expiryPhrase(c.daysToExpiry)}
        </p>
      </Td>
      <Td>
        <span className="whitespace-nowrap text-sm">
          {scheduled ? c.frequencyLabel : <span className="text-muted">Ticket-based</span>}
        </span>
      </Td>
      <Td>
        {/* A ticket-based contract has no visits, so "next visit" and progress are not blanks to
            be filled in later — they don't apply, and an em dash says that more honestly than 0/0. */}
        {!scheduled ? <span className="text-gray-400">—</span>
          : c.nextVisitDate ? (
            <span className={`whitespace-nowrap text-sm ${c.visitsOverdue > 0 ? 'font-medium text-red-600' : ''}`}>
              {formatDateOnly(c.nextVisitDate)}
            </span>
          ) : <span className="text-sm italic text-gray-400">All done</span>}
        {scheduled && c.visitsOverdue > 0 && <p className="text-xs font-medium text-red-600">{c.visitsOverdue} overdue</p>}
      </Td>
      <Td>
        {!scheduled ? <span className="text-gray-400">—</span> : (
          <div className="flex items-center gap-2">
            <span className="whitespace-nowrap text-sm"><b>{c.visitsCompleted}</b> <span className="text-muted">/ {c.visitsTotal}</span></span>
            <ProgressBar value={c.visitsCompleted} max={Math.max(c.visitsTotal, 1)} neutral className="w-16" />
          </div>
        )}
      </Td>
      <Td><GenericBadge tone={STATE_TONE[c.state]}>{STATE_LABEL[c.state]}</GenericBadge></Td>
    </Tr>
  )
}

function Stat({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: number; tone?: 'brand' | 'red' }) {
  const color = tone === 'red' ? 'text-red-600' : tone === 'brand' ? 'text-brand-600' : 'text-ink'
  return (
    <Card className="p-4">
      <div className="flex items-center gap-2 text-sm text-muted">{icon} {label}</div>
      <p className={`mt-1 text-2xl font-bold ${color}`}>{value}</p>
    </Card>
  )
}
