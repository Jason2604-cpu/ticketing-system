import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AlertCircle, ArrowUpRight, CheckCircle2, Info, Loader, Ticket, TrendingUp } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { StatCard } from '../../components/ui/StatCard'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { PriorityBadge } from '../../components/ui/Badge'
import { priorityStyles } from '../../components/ui/badgeStyles'
import { Avatar } from '../../components/ui/Avatar'
import { ProgressBar } from '../../components/ui/Misc'
import { ticketsApi } from '../../api/tickets'
import { useAuth } from '../../auth/useAuth'
import { formatShortDate, shortName, timeAgo } from '../../lib/format'
import type { TicketPriority, TicketSummary } from '../../types'
import { chartTheme, tooltipStyle } from '../../lib/chartTheme'

const PRIORITIES: TicketPriority[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

function volumeSeries(tickets: TicketSummary[], days = 30) {
  const out: { day: string; created: number; resolved: number }[] = []
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() - i)
    const next = new Date(d); next.setDate(d.getDate() + 1)
    const within = (iso?: string) => !!iso && new Date(iso) >= d && new Date(iso) < next
    out.push({
      day: formatShortDate(d),
      created: tickets.filter((t) => within(t.createdAt)).length,
      resolved: tickets.filter((t) => within(t.resolvedAt)).length,
    })
  }
  return out
}

export default function DashboardPage() {
  const { user } = useAuth()
  // See ReportView: read per render so a theme switch repaints the chart chrome.
  const ct = chartTheme()
  const { data: tickets = [] } = useQuery({ queryKey: ['tickets', 'all'], queryFn: () => ticketsApi.list() })

  const [now] = useState(() => Date.now())
  const stats = useMemo(() => {
    const weekAgo = now - 7 * 86400000
    const monthAgo = now - 30 * 86400000
    return {
      total: tickets.length,
      thisWeek: tickets.filter((t) => new Date(t.createdAt).getTime() > weekAgo).length,
      open: tickets.filter((t) => t.status === 'OPEN').length,
      inProgress: tickets.filter((t) => t.status === 'IN_PROGRESS').length,
      onHold: tickets.filter((t) => t.status === 'ON_HOLD').length,
      resolved30: tickets.filter((t) => t.resolvedAt && new Date(t.resolvedAt).getTime() > monthAgo).length,
      critical: tickets.filter((t) => t.priority === 'CRITICAL' && (t.status === 'OPEN' || t.status === 'IN_PROGRESS')).length,
    }
  }, [tickets, now])

  const byPriority = PRIORITIES.map((p) => ({ name: priorityStyles[p].label, value: tickets.filter((t) => t.priority === p).length, color: priorityStyles[p].dot }))
  const maxPriority = Math.max(1, ...byPriority.map((b) => b.value))

  const byCompany = useMemo(() => {
    const map = new Map<string, { open: number; inProgress: number; onHold: number; done: number }>()
    for (const t of tickets) {
      const row = map.get(t.companyName) ?? { open: 0, inProgress: 0, onHold: 0, done: 0 }
      if (t.status === 'OPEN') row.open++
      else if (t.status === 'IN_PROGRESS') row.inProgress++
      else if (t.status === 'ON_HOLD') row.onHold++
      else row.done++
      map.set(t.companyName, row)
    }
    return [...map.entries()].map(([name, v]) => ({ name, ...v, total: v.open + v.inProgress + v.onHold + v.done })).sort((a, b) => b.total - a.total)
  }, [tickets])

  const workload = useMemo(() => {
    const map = new Map<number, { first: string; last: string; active: number }>()
    for (const t of tickets) {
      if (!t.assignee || t.status === 'RESOLVED' || t.status === 'CLOSED') continue
      const row = map.get(t.assignee.id) ?? { first: t.assignee.firstName, last: t.assignee.lastName, active: 0 }
      row.active++
      map.set(t.assignee.id, row)
    }
    return [...map.values()].sort((a, b) => b.active - a.active)
  }, [tickets])
  const maxLoad = Math.max(1, ...workload.map((w) => w.active))

  // Tickets a customer is still waiting on: unfinished, and no engineer has publicly replied yet.
  // ON_HOLD counts — a hold pauses the resolution clock, not the first-response one. Same rule as
  // the backend's ?awaitingResponse=true filter, which is what "View" opens.
  const waiting = useMemo(() => {
    const pending = tickets.filter((t) => (t.status === 'OPEN' || t.status === 'IN_PROGRESS' || t.status === 'ON_HOLD') && !t.firstResponseAt)
    return {
      total: pending.length,
      mine: pending.filter((t) => t.assignee?.id === user?.id).length,
      unassigned: pending.filter((t) => !t.assignee).length,
    }
  }, [tickets, user?.id])

  const series = useMemo(() => volumeSeries(tickets), [tickets])
  const recent = tickets.slice(0, 4)

  return (
    <>
      <Topbar title="Overview Dashboard" />
      <Page>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Total Tickets" value={stats.total} icon={<Ticket size={18} />} tone="brand" sub={<span className="inline-flex items-center gap-1"><ArrowUpRight size={14} className="text-green-600" /> +{stats.thisWeek} this week</span>} />
          <StatCard label="Open" value={stats.open} icon={<AlertCircle size={18} />} tone="blue" sub={<span className="inline-flex items-center gap-1"><ArrowUpRight size={14} className="text-green-600" /> Needs attention</span>} />
          <StatCard label="In Progress" value={stats.inProgress} icon={<Loader size={18} />} tone="orange" sub={stats.onHold > 0 ? `Being worked on · ${stats.onHold} on hold` : 'Being worked on'} />
          <StatCard label="Resolved (30d)" value={stats.resolved30} icon={<CheckCircle2 size={18} />} tone="green" sub={<span className="inline-flex items-center gap-1"><TrendingUp size={14} className="text-green-600" /> vs last month</span>} />
        </div>

        {stats.critical > 0 && (
          <div className="mt-4 flex items-center justify-between rounded-xl border border-red-200 bg-red-50 px-5 py-3.5">
            <p className="flex items-center gap-2 text-red-600"><AlertCircle size={18} /><span><b>{stats.critical} critical ticket{stats.critical > 1 ? 's' : ''}</b> require immediate attention.</span></p>
            <Link to="/tickets?priority=CRITICAL" className="text-sm font-medium text-red-600 hover:underline">View critical →</Link>
          </div>
        )}

        {waiting.total > 0 && (
          <div className="mt-4 flex items-center justify-between rounded-xl border border-blue-200 bg-blue-50 px-5 py-3.5">
            <p className="flex items-center gap-2 text-blue-700"><Info size={18} /><span>
              <b>{waiting.total} ticket{waiting.total > 1 ? 's' : ''}</b> waiting for a first response
              {waiting.mine > 0 && (
                <> — <Link to={`/tickets?awaiting=first-response&assigneeId=${user?.id}`} className="underline-offset-2 hover:underline">{waiting.mine} assigned to you</Link></>
              )}
              {waiting.unassigned > 0 && <>{waiting.mine > 0 ? ', ' : ' — '}{waiting.unassigned} unassigned</>}.
            </span></p>
            <Link to="/tickets?awaiting=first-response" className="text-sm font-medium text-blue-700 hover:underline">View →</Link>
          </div>
        )}

        <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
          <Card className="xl:col-span-2">
            <CardHeader
              title="Ticket Volume — Last 30 Days"
              action={
                <div className="flex items-center gap-4 text-sm text-muted">
                  <span className="flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-full bg-brand-500" /> Created</span>
                  <span className="flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-full bg-green-500" /> Resolved</span>
                </div>
              }
            />
            <CardBody>
              <div className="h-64">
                <ResponsiveContainer>
                  <AreaChart data={series} margin={{ left: -20, right: 8, top: 8 }}>
                    <defs>
                      <linearGradient id="gCreated" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#6366f1" stopOpacity={0.25} /><stop offset="100%" stopColor="#6366f1" stopOpacity={0} /></linearGradient>
                      <linearGradient id="gResolved" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#22c55e" stopOpacity={0.2} /><stop offset="100%" stopColor="#22c55e" stopOpacity={0} /></linearGradient>
                    </defs>
                    <CartesianGrid vertical={false} strokeDasharray="3 3" stroke={ct.grid} />
                    <XAxis dataKey="day" tick={{ fontSize: 12, fill: ct.axis }} axisLine={false} tickLine={false} interval={8} />
                    <YAxis tick={{ fontSize: 12, fill: ct.axis }} axisLine={false} tickLine={false} allowDecimals={false} />
                    <Tooltip contentStyle={tooltipStyle(ct)} />
                    <Area type="monotone" dataKey="created" stroke="#6366f1" strokeWidth={2} fill="url(#gCreated)" />
                    <Area type="monotone" dataKey="resolved" stroke="#22c55e" strokeWidth={2} fill="url(#gResolved)" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="By Priority" />
            <CardBody>
              <div className="mx-auto h-36 w-36">
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={byPriority} dataKey="value" innerRadius={40} outerRadius={64} paddingAngle={3} stroke="none">
                      {byPriority.map((b) => <Cell key={b.name} fill={b.color} />)}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <ul className="mt-4 space-y-2.5">
                {byPriority.map((b) => (
                  <li key={b.name} className="flex items-center gap-3 text-sm">
                    <i className="h-2.5 w-2.5 rounded-full" style={{ background: b.color }} />
                    <span className="text-gray-700">{b.name}</span>
                    <span className="ml-auto font-medium">{b.value}</span>
                    <ProgressBar value={b.value} max={maxPriority} neutral className="w-16" />
                  </li>
                ))}
              </ul>
            </CardBody>
          </Card>
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
          <Card>
            <CardHeader title="By Company" />
            <CardBody className="space-y-4">
              {byCompany.map((c) => (
                <div key={c.name}>
                  <div className="flex justify-between text-sm"><span className="font-medium">{c.name}</span><span className="text-muted">{c.total} tickets</span></div>
                  <div className="mt-2 flex h-1.5 gap-1">
                    {c.open > 0 && <div className="rounded-full bg-blue-500" style={{ flex: c.open }} />}
                    {c.inProgress > 0 && <div className="rounded-full bg-orange-500" style={{ flex: c.inProgress }} />}
                    {c.onHold > 0 && <div className="rounded-full bg-purple-500" style={{ flex: c.onHold }} title={`${c.onHold} on hold`} />}
                    {c.done > 0 && <div className="rounded-full bg-green-500" style={{ flex: c.done }} />}
                  </div>
                </div>
              ))}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Engineer Workload" />
            <CardBody className="space-y-4">
              {workload.map((w) => (
                <div key={w.first + w.last} className="flex items-center gap-3">
                  <Avatar firstName={w.first} lastName={w.last} />
                  <div className="flex-1">
                    <div className="flex justify-between text-sm"><span className="font-medium">{shortName(w.first, w.last)}</span><span className="text-muted">{w.active} active</span></div>
                    <ProgressBar value={w.active} max={maxLoad} neutral className="mt-2" />
                  </div>
                </div>
              ))}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Recent Tickets" action={<Link to="/tickets" className="text-sm font-medium text-brand-600 hover:underline">View all</Link>} />
            <CardBody className="space-y-4">
              {recent.map((t) => (
                <Link key={t.id} to={`/tickets/${t.id}`} className="flex items-start gap-3 rounded-lg hover:bg-gray-50">
                  <PriorityBadge priority={t.priority} />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{t.title}</p>
                    <p className="text-sm text-muted">{t.companyName} · {timeAgo(t.createdAt)}</p>
                  </div>
                </Link>
              ))}
            </CardBody>
          </Card>
        </div>
      </Page>
    </>
  )
}
