import { Link } from 'react-router-dom'
import { AlertTriangle, Check, Clock3, RefreshCcw, ShieldCheck, Zap } from 'lucide-react'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { StatCard } from '../../components/ui/StatCard'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Avatar } from '../../components/ui/Avatar'
import { ProgressBar, EmptyState } from '../../components/ui/Misc'
import { priorityStyles } from '../../components/ui/badgeStyles'
import { shortName } from '../../lib/format'
import type { Report } from '../../types'
import { chartTheme, tooltipStyle } from '../../lib/chartTheme'

const SLA_TARGET = 90
const STATUS_COLORS: Record<string, string> = {
  OPEN: '#3b82f6', IN_PROGRESS: '#f97316', RESOLVED: '#22c55e', CLOSED: '#9ca3af',
}
/** Widened to string keys: the API sends priority codes as plain strings. */
const PRIORITY_DOTS: Record<string, string> = Object.fromEntries(
  Object.entries(priorityStyles).map(([k, v]) => [k, v.dot]),
)

/** "2026-09-04" → "4 Sep"; "2026-09" → "Sep 26". Keeps the x-axis readable at both granularities. */
function tickLabel(period: string, granularity: 'DAY' | 'MONTH'): string {
  const [y, m, d] = period.split('-')
  const month = new Date(Number(y), Number(m) - 1, 1).toLocaleString('en', { month: 'short' })
  return granularity === 'DAY' ? `${Number(d)} ${month}` : `${month} ${y.slice(2)}`
}

/**
 * The report body, shared by the staff Analytics page and the client Reports page.
 *
 * Everything here is read straight off the server's aggregate — no arithmetic in the browser,
 * which is what keeps the numbers right past the ticket list's 500-row cap.
 * `showCompanies` is off for the client portal, where there is only ever one company.
 */
export function ReportView({ report, showCompanies = true, showEngineers = true }: {
  report: Report
  showCompanies?: boolean
  showEngineers?: boolean
}) {
  // Re-read on every render: the CSS variables change when the theme is toggled,
  // and ThemeToggle's context update re-renders this tree.
  const ct = chartTheme()
  const { totals, sla } = report
  const series = report.series.map((b) => ({ ...b, label: tickLabel(b.period, report.granularity) }))
  const slaGood = sla.resolutionCompliancePct >= SLA_TARGET
  const priorityData = report.byPriority
    .filter((s) => s.count > 0)
    .map((s) => ({ name: s.label, value: s.count, color: PRIORITY_DOTS[s.key] ?? '#9ca3af' }))
  const statusData = report.byStatus.filter((s) => s.count > 0)
  // Show at most ~12 x-axis labels regardless of range, so a 12-month or 90-day view stays legible.
  const tickInterval = Math.max(0, Math.floor(series.length / 12))

  return (
    <>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Tickets Created" value={totals.created} icon={<RefreshCcw size={18} />} tone="brand" sub={`${totals.resolved} resolved in range`} />
        {/* Working hours, not elapsed — these are measured on the same business calendar that sets
            the SLA deadlines, so a Friday-evening ticket answered Monday morning reads as one hour
            here and as met over there, instead of 64h beside 100% compliance. */}
        <StatCard label="Avg Resolution Time" value={totals.resolved ? `${totals.avgResolutionHours}h` : '—'} icon={<Clock3 size={18} />} tone="green" sub={`First response ${totals.avgFirstResponseHours}h · business hours`} />
        <StatCard
          label="SLA Compliance"
          value={sla.measured ? `${sla.resolutionCompliancePct}%` : '—'}
          icon={<ShieldCheck size={18} />}
          tone={sla.measured && !slaGood ? 'orange' : 'green'}
          // Naming the denominator matters: compliance is over the tickets whose clock has stopped
          // AND had a target, which is a subset of the ones created in the range.
          sub={sla.measured
            ? `${sla.resolutionMet} met · ${sla.resolutionMissed} missed of ${sla.measured} measured · target ${SLA_TARGET}%`
            : 'No tickets finished with an SLA target yet'}
        />
        <StatCard
          label="Currently Breaching"
          value={sla.currentlyBreaching}
          icon={<AlertTriangle size={18} />}
          tone={sla.currentlyBreaching > 0 ? 'red' : 'green'}
          // Live figure — it deliberately ignores the date range, which surprises people looking at
          // a past period, so the label says so rather than leaving them to guess.
          // These two cards are related but not the same number: this one is live and unbounded by
          // the date range, and counts tickets, while missed counts clocks within the range.
          sub={sla.currentlyBreaching > 0 ? 'Open past a deadline · right now, any date' : 'Nothing overdue right now'}
        />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader title={`Tickets per ${report.granularity === 'DAY' ? 'day' : 'month'}`} />
          <CardBody>
            <div className="h-60">
              <ResponsiveContainer>
                <AreaChart data={series} margin={{ left: -20, right: 8, top: 8 }}>
                  <defs><linearGradient id="rCreated" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#6366f1" stopOpacity={0.25} /><stop offset="100%" stopColor="#6366f1" stopOpacity={0} /></linearGradient></defs>
                  <CartesianGrid vertical={false} strokeDasharray="3 3" stroke={ct.grid} />
                  <XAxis dataKey="label" tick={{ fontSize: 12, fill: ct.axis }} axisLine={false} tickLine={false} interval={tickInterval} />
                  <YAxis tick={{ fontSize: 12, fill: ct.axis }} axisLine={false} tickLine={false} allowDecimals={false} />
                  <Tooltip contentStyle={tooltipStyle(ct)} />
                  <Area type="monotone" dataKey="created" name="Created" stroke="#6366f1" strokeWidth={2} fill="url(#rCreated)" />
                  <Area type="monotone" dataKey="resolved" name="Resolved" stroke="#22c55e" strokeWidth={2} fill="transparent" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-2 flex justify-center gap-5 text-sm text-muted">
              <span className="flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-full bg-brand-500" /> Created</span>
              <span className="flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-full bg-green-500" /> Resolved</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Tickets by Priority" />
          <CardBody>
            {priorityData.length === 0 ? (
              <EmptyState title="No tickets in this range" hint="Try a wider date range." />
            ) : (
              <div className="h-60">
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={priorityData} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} paddingAngle={2}>
                      {priorityData.map((d) => <Cell key={d.name} fill={d.color} />)}
                    </Pie>
                    <Tooltip contentStyle={tooltipStyle(ct)} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            )}
            <div className="mt-2 flex flex-wrap justify-center gap-4 text-sm text-muted">
              {priorityData.map((d) => (
                <span key={d.name} className="flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: d.color }} /> {d.name} ({d.value})</span>
              ))}
            </div>
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader title="Tickets by Category" />
          <CardBody>
            <div className="h-56">
              <ResponsiveContainer>
                <BarChart data={report.byCategory} margin={{ left: -20, right: 8, top: 8 }}>
                  <CartesianGrid vertical={false} strokeDasharray="3 3" stroke={ct.grid} />
                  <XAxis dataKey="label" tick={{ fontSize: 12, fill: ct.axis }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 12, fill: ct.axis }} axisLine={false} tickLine={false} allowDecimals={false} />
                  <Tooltip cursor={{ fill: ct.cursor }} contentStyle={tooltipStyle(ct)} />
                  <Bar dataKey="count" name="Tickets" fill="#6366f1" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Status of tickets raised in range" />
          <CardBody className="space-y-3">
            {statusData.length === 0 && <EmptyState title="No tickets in this range" hint="Try a wider date range." />}
            {statusData.map((s) => (
              <div key={s.key}>
                <div className="flex justify-between text-sm"><span>{s.label}</span><span className="font-semibold">{s.count}</span></div>
                <ProgressBar value={s.count} max={Math.max(1, totals.created)} color={STATUS_COLORS[s.key] ?? '#6366f1'} className="mt-1.5" height="h-2" />
              </div>
            ))}
            <div className="space-y-1.5 border-t border-line pt-3 text-sm">
              <div className="flex justify-between">
                <span className="text-muted">First response SLA</span>
                <span className="font-semibold">{sla.responseMet + sla.responseMissed > 0 ? `${sla.responseCompliancePct}% (${sla.responseMet} met · ${sla.responseMissed} missed)` : '—'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">Resolution SLA</span>
                <span className="font-semibold">{sla.measured > 0 ? `${sla.resolutionCompliancePct}% (${sla.resolutionMet} met · ${sla.resolutionMissed} missed)` : '—'}</span>
              </div>
              <p className="pt-1 text-xs text-muted">
                Measured over {sla.measured} of {sla.total} tickets created in this range. A ticket counts as missed
                the moment its deadline passes, not when it's finally closed — the rest are still inside their
                deadline, or have no SLA target on their plan.
              </p>
            </div>
          </CardBody>
        </Card>
      </div>

      {showCompanies && (
        <Card className="mt-4">
          <CardHeader title="By Company" action={<span className="text-sm text-muted">SLA target: {SLA_TARGET}%</span>} />
          <CardBody className="space-y-4">
            {report.byCompany.length === 0 && <EmptyState title="No tickets in this range" hint="Try a wider date range." />}
            {report.byCompany.map((c) => {
              const good = c.slaPct >= SLA_TARGET
              return (
                <div key={c.companyId} className="grid grid-cols-[minmax(120px,180px)_1fr_70px_90px] items-center gap-4 text-sm">
                  <Link to={`/companies/${c.companyId}`} className="truncate font-medium text-brand-600 hover:underline">{c.companyName}</Link>
                  <ProgressBar value={c.slaPct} color={good ? '#22c55e' : '#f59e0b'} height="h-2.5" />
                  <span className="text-muted">{c.created} raised</span>
                  <span className={good ? 'flex items-center gap-1 font-semibold text-green-600' : 'flex items-center gap-1 font-semibold text-amber-600'}>
                    {good ? <Check size={14} /> : <AlertTriangle size={14} />}{c.slaPct}%
                  </span>
                </div>
              )
            })}
          </CardBody>
        </Card>
      )}

      {showEngineers && (
        <Card className="mt-4">
          <CardHeader title="Engineer Performance" action={<span className="flex items-center gap-1 text-sm text-muted"><Zap size={14} /> assigned in range</span>} />
          <CardBody>
            {report.byEngineer.length === 0 ? (
              <EmptyState title="No assigned tickets in this range" hint="Assign tickets to see engineer performance." />
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                {report.byEngineer.map((e) => (
                  <div key={e.userId} className="flex items-center gap-3 rounded-lg border border-line p-4">
                    <Avatar firstName={e.firstName} lastName={e.lastName} size="lg" />
                    <div className="min-w-0">
                      <p className="truncate font-medium">{shortName(e.firstName, e.lastName)}</p>
                      <p className="text-sm text-muted">{e.resolved} of {e.assigned} resolved</p>
                      <p className="text-xs text-muted">avg {e.avgResolutionHours}h</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardBody>
        </Card>
      )}
    </>
  )
}
