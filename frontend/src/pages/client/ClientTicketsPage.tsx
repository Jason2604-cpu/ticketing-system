import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Calendar, MessageSquare, Search, User as UserIcon } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { PriorityBadge, SlaBadge, StatusBadge } from '../../components/ui/Badge'
import { useFeature } from '../../lib/useFeature'
import { EmptyState } from '../../components/ui/Misc'
import { ticketsApi } from '../../api/tickets'
import { useAuth } from '../../auth/useAuth'
import { cn } from '../../lib/cn'
import { formatShortDate, shortName, timeAgo } from '../../lib/format'
import type { TicketStatus } from '../../types'

const TABS: { key: TicketStatus | ''; label: string }[] = [
  { key: '', label: 'All' }, { key: 'OPEN', label: 'Open' }, { key: 'IN_PROGRESS', label: 'In Progress' }, { key: 'RESOLVED', label: 'Resolved' }, { key: 'CLOSED', label: 'Closed' },
]

export default function ClientTicketsPage() {
  const { user } = useAuth()
  // SLA visibility is a paid feature — a Starter customer sees no SLA badges at all.
  const hasSla = useFeature('SLA_MANAGEMENT')
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? '') as TicketStatus | ''
  const [q, setQ] = useState(params.get('q') ?? '')

  const { data: all = [] } = useQuery({ queryKey: ['tickets', 'all'], queryFn: () => ticketsApi.list() })
  const mine = all.filter((t) => t.companyId === user?.companyId)
  const count = (s: TicketStatus | '') => (s ? mine.filter((t) => t.status === s).length : mine.length)
  const shown = mine
    .filter((t) => !status || t.status === status)
    .filter((t) => !q.trim() || t.title.toLowerCase().includes(q.toLowerCase()) || String(t.id).includes(q))

  return (
    <>
      <Topbar title="My Tickets" search={false} />
      <Page>
        <div className="flex flex-wrap gap-2">
          {TABS.map((t) => (
            <button key={t.key} onClick={() => setParams(t.key ? { status: t.key } : {}, { replace: true })}
              className={cn('inline-flex items-center gap-2 rounded-lg border px-4 py-2 text-sm font-medium', status === t.key ? 'border-brand-600 bg-brand-600 text-white' : 'border-line bg-surface text-gray-700 hover:bg-gray-50')}>
              {t.label}
              <span className={cn('rounded-full px-1.5 text-xs', status === t.key ? 'bg-surface/20' : 'bg-gray-100 text-muted')}>{count(t.key)}</span>
            </button>
          ))}
        </div>
        <div className="relative mt-3">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search your tickets…" className="h-11 w-full rounded-lg border border-line bg-surface pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
        </div>

        <Card className="mt-4 divide-y divide-line">
          {shown.map((t) => (
            <Link key={t.id} to={`/tickets/${t.id}`} className="block px-5 py-4 hover:bg-gray-50">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2"><PriorityBadge priority={t.priority} /><StatusBadge status={t.status} />{hasSla && <SlaBadge state={t.resolutionSla} quiet />}</div>
                  <p className="mt-2 font-medium">{t.title}</p>
                  <div className="mt-1.5 flex flex-wrap items-center gap-4 text-sm text-muted">
                    <span className="flex items-center gap-1"><UserIcon size={13} /> {t.assignee ? shortName(t.assignee.firstName, t.assignee.lastName) : 'Unassigned'}</span>
                    <span className="flex items-center gap-1"><MessageSquare size={13} /> {t.commentCount}</span>
                    <span className="flex items-center gap-1"><Calendar size={13} /> {formatShortDate(t.createdAt)}</span>
                  </div>
                </div>
                <div className="shrink-0 text-right text-sm text-muted"><p className="font-mono">#{t.id}</p><p className="mt-6">{timeAgo(t.updatedAt)}</p></div>
              </div>
            </Link>
          ))}
          {shown.length === 0 && <EmptyState title="No tickets here" hint={status ? 'Nothing in this status.' : 'Submit a ticket and it will show up here.'} />}
        </Card>
      </Page>
    </>
  )
}
