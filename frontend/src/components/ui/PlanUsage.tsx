import { Alert, ProgressBar } from './Misc'
import { formatBytes } from '../../lib/format'
import type { CompanySubscription } from '../../types'

/** Bar colour by how close a quota is to full — brand until it matters, red once it's effectively spent. */
function usageTone(pct: number) {
  if (pct >= 100) return '#ef4444'
  if (pct >= 80) return '#f97316'
  return '#4f46e5'
}

function UsageRow({ label, used, limit, pct }: { label: string; used: string; limit: string; pct: number }) {
  return (
    <div>
      <div className="flex justify-between text-sm">
        <span>{label}</span>
        <span className="font-semibold">{used} <span className="font-normal text-muted">of {limit}</span></span>
      </div>
      <ProgressBar value={Math.min(100, pct)} color={usageTone(pct)} className="mt-1.5" />
    </div>
  )
}

/**
 * What a company has consumed against its plan: storage, tickets this month, seats — with
 * warnings as they fill. Storage is the one the backend enforces on upload, so it comes first and
 * warns before uploads start failing.
 *
 * Shared by the provider's company page and the client portal's Company Info. `audience` only
 * changes the warning wording: staff are told what the system is doing, a customer what to do.
 */
export function PlanUsageRows({ sub, audience = 'staff' }: { sub: CompanySubscription; audience?: 'staff' | 'customer' }) {
  const storageLimitBytes = sub.limits.storageLimitMb * 1024 * 1024
  const storagePct = storageLimitBytes > 0 ? (sub.usage.storageUsedBytes / storageLimitBytes) * 100 : 0
  const ticketPct = sub.limits.monthlyTicketLimit > 0 ? (sub.usage.ticketsThisMonth / sub.limits.monthlyTicketLimit) * 100 : 0
  const seatPct = sub.limits.maxUsers > 0 ? (sub.usage.activeUsers / sub.limits.maxUsers) * 100 : 0
  const customer = audience === 'customer'

  return (
    <div className="space-y-3">
      <UsageRow
        label="Storage"
        used={formatBytes(sub.usage.storageUsedBytes)}
        limit={sub.limits.storageLimitMb >= 1024 ? `${Math.round(sub.limits.storageLimitMb / 1024)} GB` : `${sub.limits.storageLimitMb} MB`}
        pct={storagePct}
      />
      <UsageRow label="Tickets this month" used={String(sub.usage.ticketsThisMonth)} limit={String(sub.limits.monthlyTicketLimit)} pct={ticketPct} />
      <UsageRow label="Seats" used={String(sub.usage.activeUsers)} limit={String(sub.limits.maxUsers)} pct={seatPct} />

      {storagePct >= 100 && (
        <Alert tone="error">
          {customer
            ? 'Storage is full — new attachments are being rejected. Contact support to upgrade your plan.'
            : 'Storage is full — new attachments and logo uploads are being rejected.'}
        </Alert>
      )}
      {storagePct >= 80 && storagePct < 100 && <Alert tone="warning">Storage is {Math.round(storagePct)}% used.</Alert>}
      {ticketPct >= 100 && (
        <Alert tone="error">
          {customer
            ? "Monthly ticket limit reached — new tickets can't be submitted until next month. Contact support to upgrade your plan."
            : 'The monthly ticket limit is reached — new tickets are being rejected until next month.'}
        </Alert>
      )}
    </div>
  )
}
