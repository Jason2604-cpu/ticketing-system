import { useMemo, useState } from 'react'
import { ROLE_META } from '../../lib/roleMeta'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Download, ScrollText, Search, ShieldAlert } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { GenericBadge } from '../../components/ui/Badge'
import { EmptyState } from '../../components/ui/Misc'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { auditApi, type AuditFilters } from '../../api/audit'
import type { AuditEvent, AuditLevel, Role } from '../../types'

const PAGE_SIZE = 50

/**
 * Turns SCREAMING_SNAKE action names into something readable, without a hand-maintained map of
 * all ~55 of them — a new audit action added anywhere in the backend shows up here correctly
 * with no frontend change.
 */
const humanAction = (action: string) =>
  action.toLowerCase().replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase())

// The shared label when it's a role we know (STAFF reads "Default"); old rows fall back to the raw name.
const roleLabel = (role: string) =>
  role in ROLE_META ? ROLE_META[role as Role].label : role.charAt(0) + role.slice(1).toLowerCase()

/**
 * Groups actions by their first word (TICKET_*, USER_*, LOGIN_*) purely for the filter dropdown,
 * so a list of 55 flat options is navigable.
 */
const actionGroup = (action: string) => action.split('_')[0]

/** Which colour a row's action reads as. Security events earn attention; the rest are chrome. */
const actionTone = (action: string, level: AuditLevel): 'gray' | 'red' | 'green' | 'blue' | 'purple' | 'brand' => {
  if (level === 'WARN') return 'red'
  if (action.startsWith('LOGIN') || action.startsWith('PASSWORD') || action.startsWith('ACCOUNT')) return 'purple'
  if (action.includes('DELETED') || action.includes('REMOVED')) return 'red'
  if (action.includes('CREATED')) return 'green'
  return 'blue'
}

/**
 * Admin Panel → Audit Log. Every AuditLogger event the backend records: sign-ins, permission
 * changes, record edits, rate-limit rejections.
 *
 * Read-only, deliberately — there is no delete on the API either. An audit log an admin can
 * prune by hand is not evidence of anything. Trimming happens only through the configured
 * retention window (app.audit.retention-days).
 */
export default function AuditLogPage() {
  const [action, setAction] = useState('')
  const [level, setLevel] = useState<AuditLevel | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState<number | null>(null)

  const filters: AuditFilters = useMemo(() => ({
    action: action || undefined,
    level: level || undefined,
    from: from || undefined,
    to: to || undefined,
    q: q || undefined,
  }), [action, level, from, to, q])

  const data = useQuery({
    queryKey: ['audit', filters, page],
    queryFn: () => auditApi.list(filters, page, PAGE_SIZE),
    // The audit log is a record of the past, not a live feed — opting out of the app's 15s poll
    // keeps a page you're reading from reshuffling under you.
    refetchInterval: false,
    staleTime: 30_000,
  })

  /** Reset to the first page whenever a filter changes — page 7 of a narrower result is nowhere. */
  const applyFilter = (fn: () => void) => { fn(); setPage(0) }

  const rows = data.data?.events ?? []
  const total = data.data?.totalElements ?? 0
  const totalPages = data.data?.totalPages ?? 0
  const actions = data.data?.availableActions ?? []
  const anyFilter = !!(action || level || from || to || q)

  const actionOptions = useMemo(() => {
    const sorted = [...actions].sort((a, b) => actionGroup(a).localeCompare(actionGroup(b)) || a.localeCompare(b))
    return [
      { value: 'ALL', label: 'All actions' },
      ...sorted.map((a) => ({ value: a, label: humanAction(a), description: actionGroup(a) })),
    ]
  }, [actions])

  return (
    <>
      <Topbar
        title="Audit Log"
        subtitle="Sign-ins, permission changes and record edits"
        search={false}
        newTicket={false}
        actions={
          <a href={auditApi.exportUrl(filters)} download>
            <Button variant="secondary"><Download size={15} /> Export CSV</Button>
          </a>
        }
      />
      <Page>
        <Card className="mb-4 p-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <div className="relative flex-1">
              <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
              <Input
                className="pl-9"
                placeholder="Search details, actor, email or IP…"
                value={q}
                onChange={(e) => applyFilter(() => setQ(e.target.value))}
              />
            </div>
            <SelectMenu
              className="lg:w-56"
              value={action}
              onChange={(v) => applyFilter(() => setAction(v === 'ALL' ? '' : String(v)))}
              placeholder="All actions"
              options={actionOptions}
            />
            <SelectMenu
              className="lg:w-40"
              value={level}
              onChange={(v) => applyFilter(() => setLevel(v === 'ALL' ? '' : (v as AuditLevel)))}
              placeholder="Any level"
              options={[
                { value: 'ALL', label: 'Any level' },
                { value: 'INFO', label: 'Info', description: 'Allowed and completed' },
                { value: 'WARN', label: 'Warning', description: 'Refused, failed or rate-limited' },
              ]}
            />
            <div className="flex items-center gap-2">
              <Input type="date" className="w-40" value={from} onChange={(e) => applyFilter(() => setFrom(e.target.value))} aria-label="From date" />
              <span className="text-sm text-muted">to</span>
              <Input type="date" className="w-40" value={to} onChange={(e) => applyFilter(() => setTo(e.target.value))} aria-label="To date" />
            </div>
          </div>
          {anyFilter && (
            <div className="mt-2 flex items-center gap-3 px-1 text-xs text-muted">
              <span>{total.toLocaleString()} matching event{total === 1 ? '' : 's'}</span>
              <button
                className="font-medium text-brand-600 hover:underline"
                onClick={() => applyFilter(() => { setAction(''); setLevel(''); setFrom(''); setTo(''); setQ('') })}
              >
                Clear filters
              </button>
            </div>
          )}
        </Card>

        {data.isLoading ? (
          <Card><div className="p-10 text-center text-sm text-muted">Loading the trail…</div></Card>
        ) : rows.length === 0 ? (
          <Card>
            <EmptyState
              icon={<ScrollText size={22} />}
              title={anyFilter ? 'No events match those filters' : 'Nothing recorded yet'}
              hint={anyFilter
                ? 'Try widening the date range or clearing the action filter.'
                : 'Events appear here as people sign in and make changes. Anything recorded before the audit table existed is only in the application log.'}
            />
          </Card>
        ) : (
          <>
            <Card className="overflow-hidden">
              <div className="overflow-x-auto">
                <Table>
                  <thead className="bg-gray-50/60">
                    <tr><Th>When</Th><Th>Action</Th><Th>Who</Th><Th>Details</Th><Th>IP</Th></tr>
                  </thead>
                  <tbody>
                    {rows.map((e) => (
                      <AuditRow key={e.id} e={e} expanded={expanded === e.id} onToggle={() => setExpanded(expanded === e.id ? null : e.id)} />
                    ))}
                  </tbody>
                </Table>
              </div>
            </Card>

            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-muted">
                Showing {(page * PAGE_SIZE + 1).toLocaleString()}–{Math.min((page + 1) * PAGE_SIZE, total).toLocaleString()} of {total.toLocaleString()}
              </p>
              <div className="flex items-center gap-2">
                <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                  <ChevronLeft size={15} /> Previous
                </Button>
                <span className="text-sm text-muted">Page {page + 1} of {Math.max(totalPages, 1)}</span>
                <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
                  Next <ChevronRight size={15} />
                </Button>
              </div>
            </div>
          </>
        )}
      </Page>
    </>
  )
}

function AuditRow({ e, expanded, onToggle }: { e: AuditEvent; expanded: boolean; onToggle: () => void }) {
  const when = new Date(e.createdAt)
  const long = (e.details?.length ?? 0) > 70

  return (
    <Tr onClick={long ? onToggle : undefined}>
      <Td>
        <p className="whitespace-nowrap text-sm">{when.toLocaleDateString()}</p>
        <p className="whitespace-nowrap text-xs text-muted">{when.toLocaleTimeString()}</p>
      </Td>
      <Td>
        <div className="flex items-center gap-1.5">
          {e.level === 'WARN' && <ShieldAlert size={14} className="shrink-0 text-red-600" />}
          <GenericBadge tone={actionTone(e.action, e.level)}>{humanAction(e.action)}</GenericBadge>
        </div>
      </Td>
      <Td>
        {/*
          Three shapes, because the backend records three: a real user (name + email), an attempted
          identity with no account behind it (email only — a sign-in against an address that doesn't
          exist, which is exactly what you want to see in a security log), and genuinely
          system-initiated work like a startup seeder or a scheduled sweep.
        */}
        {e.actorName ? (
          <div>
            <p className="whitespace-nowrap text-sm font-medium">{e.actorName}</p>
            <p className="whitespace-nowrap text-xs text-muted">
              {e.actorEmail ?? e.actorRole ?? ''}
              {e.actorEmail && e.actorRole && <span className="ml-1.5 text-gray-400">· {roleLabel(e.actorRole)}</span>}
            </p>
          </div>
        ) : e.actorEmail ? (
          <div>
            <p className="whitespace-nowrap text-sm">{e.actorEmail}</p>
            <p className="whitespace-nowrap text-xs italic text-gray-400">No matching account</p>
          </div>
        ) : (
          <span className="text-sm italic text-gray-400">System</span>
        )}
      </Td>
      <Td>
        {e.details
          ? <p className={`font-mono text-xs ${expanded ? 'whitespace-pre-wrap break-all' : 'max-w-md truncate'}`}>{e.details}</p>
          : <span className="text-gray-400">—</span>}
        {long && !expanded && <span className="text-xs text-brand-600">show more</span>}
      </Td>
      <Td><span className="whitespace-nowrap font-mono text-xs text-muted">{e.ipAddress ?? '—'}</span></Td>
    </Tr>
  )
}
