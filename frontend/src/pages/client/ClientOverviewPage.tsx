import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AlertCircle, BarChart3, CheckCircle2, ChevronRight, Loader, MessageSquare, Plus, RefreshCw, UserCheck } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { StatCard } from '../../components/ui/StatCard'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { PriorityBadge, StatusBadge } from '../../components/ui/Badge'
import { EmptyState } from '../../components/ui/Misc'
import { ticketsApi } from '../../api/tickets'
import { useAuth } from '../../auth/useAuth'
import { timeAgo } from '../../lib/format'

export default function ClientOverviewPage() {
  const { user } = useAuth()
  const { data: all = [] } = useQuery({ queryKey: ['tickets', 'all'], queryFn: () => ticketsApi.list() })
  const tickets = all.filter((t) => t.companyId === user?.companyId)

  const open = tickets.filter((t) => t.status === 'OPEN').length
  const inProgress = tickets.filter((t) => t.status === 'IN_PROGRESS').length
  const done = tickets.filter((t) => t.status === 'RESOLVED' || t.status === 'CLOSED').length

  // Activity feed derived from ticket state (real events arrive with the ticket backend).
  const feed = tickets.slice(0, 4).map((t) => {
    if (t.status === 'RESOLVED' || t.status === 'CLOSED') return { id: t.id, icon: <CheckCircle2 size={14} />, tone: 'bg-green-100 text-green-600', text: `Ticket #${t.id} was marked as ${t.status === 'CLOSED' ? 'Closed' : 'Resolved'}`, at: t.updatedAt }
    if (t.assignee) return { id: t.id, icon: <UserCheck size={14} />, tone: 'bg-blue-100 text-blue-600', text: `Your ticket was assigned to ${t.assignee.firstName} ${t.assignee.lastName}`, at: t.updatedAt }
    return { id: t.id, icon: <MessageSquare size={14} />, tone: 'bg-brand-100 text-brand-600', text: `Ticket #${t.id} is waiting for an engineer`, at: t.createdAt }
  })

  return (
    <>
      <Topbar title="My Overview" />
      <Page>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <StatCard label="Open Tickets" value={open} icon={<AlertCircle size={18} />} tone="blue" sub="Awaiting response" />
          <StatCard label="In Progress" value={inProgress} icon={<Loader size={18} />} tone="orange" sub="Being worked on" />
          <StatCard label="Resolved / Closed" value={done} icon={<CheckCircle2 size={18} />} tone="green" sub="This month" />
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[3fr_2fr]">
          <Card>
            <CardHeader title="Recent Tickets" action={<Link to="/tickets" className="text-sm font-medium text-brand-600 hover:underline">View all</Link>} />
            <CardBody className="divide-y divide-line">
              {tickets.slice(0, 5).map((t) => (
                <Link key={t.id} to={`/tickets/${t.id}`} className="flex items-center gap-3 py-3 hover:bg-gray-50">
                  <PriorityBadge priority={t.priority} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{t.title}</p>
                    <p className="text-sm text-muted">#{t.id} · {timeAgo(t.createdAt)}</p>
                  </div>
                  <StatusBadge status={t.status} />
                  <ChevronRight size={16} className="text-gray-300" />
                </Link>
              ))}
              {tickets.length === 0 && <EmptyState title="No tickets yet" hint="Submit your first ticket and it will show up here." action={<Link to="/tickets/new"><Button /></Link>} />}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Activity Feed" />
            <CardBody className="space-y-4">
              {feed.map((f, i) => (
                <div key={i} className="flex items-start gap-3">
                  <span className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${f.tone}`}>{f.icon}</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm">{f.text}</p>
                    <p className="text-xs text-muted">{timeAgo(f.at)}</p>
                  </div>
                  <Link to={`/tickets/${f.id}`} className="text-sm font-medium text-brand-600 hover:underline">View</Link>
                </div>
              ))}
              {feed.length === 0 && <p className="text-sm text-muted">Nothing yet.</p>}
            </CardBody>
          </Card>
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3">
          <Link to="/tickets/new" className="rounded-xl border border-brand-200 bg-brand-50 p-5 transition-colors hover:bg-brand-100">
            <Plus size={22} className="text-brand-600" />
            <p className="mt-3 font-semibold">Submit Ticket</p>
            <p className="text-sm text-muted">Report a new issue</p>
          </Link>
          <Link to="/tickets" className="rounded-xl border border-blue-200 bg-blue-50 p-5 transition-colors hover:bg-blue-100">
            <RefreshCw size={22} className="text-blue-600" />
            <p className="mt-3 font-semibold">Check Status</p>
            <p className="text-sm text-muted">View all your tickets</p>
          </Link>
          <Link to="/analytics" className="rounded-xl border border-line bg-surface p-5 transition-colors hover:bg-gray-50">
            <BarChart3 size={22} className="text-gray-600" />
            <p className="mt-3 font-semibold">View Analytics</p>
            <p className="text-sm text-muted">Your support history</p>
          </Link>
        </div>
      </Page>
    </>
  )
}

function Button() {
  return <span className="inline-flex h-9 items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white">New ticket</span>
}
