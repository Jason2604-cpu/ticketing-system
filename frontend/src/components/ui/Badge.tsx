import { cn } from '../../lib/cn'
import type { PlanStatus, SlaState, SubscriptionStatus, TicketPriority, TicketStatus, UserStatus } from '../../types'
import { priorityStyles, slaStyles, statusStyles } from './badgeStyles'

const pill = 'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap'

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  const s = priorityStyles[priority]
  return <span className={cn(pill, s.pill)}>{s.label}</span>
}

/**
 * SLA standing for one clock. Renders nothing when the plan sets no target for this priority,
 * so a Starter customer's tickets don't show an empty or misleading badge.
 */
export function SlaBadge({ state, quiet = false }: { state?: SlaState; quiet?: boolean }) {
  if (!state) return null
  const s = slaStyles[state]
  if (!s) return null
  if (quiet && (state === 'ON_TRACK' || state === 'MET')) return null
  return <span className={cn(pill, s.pill)}>{s.label}</span>
}

export function StatusBadge({ status }: { status: TicketStatus }) {
  const s = statusStyles[status]
  return <span className={cn(pill, s.pill)}>{s.label}</span>
}

export function UserStatusBadge({ status }: { status: UserStatus }) {
  const map: Record<UserStatus, string> = {
    ACTIVE: 'bg-green-100 text-green-600',
    INVITED: 'bg-yellow-100 text-yellow-700',
    INACTIVE: 'bg-gray-100 text-gray-600',
    IS_DELETED: 'bg-red-100 text-red-600',
  }
  return <span className={cn(pill, map[status])}>{status === 'IS_DELETED' ? 'Deleted' : status.charAt(0) + status.slice(1).toLowerCase()}</span>
}

export type BadgeTone = 'gray' | 'brand' | 'green' | 'red' | 'purple' | 'blue' | 'amber'

export function GenericBadge({ children, tone = 'gray' }: { children: React.ReactNode; tone?: BadgeTone }) {
  const map: Record<BadgeTone, string> = {
    gray: 'bg-gray-100 text-gray-600',
    brand: 'bg-brand-100 text-brand-700',
    green: 'bg-green-100 text-green-600',
    red: 'bg-red-100 text-red-600',
    purple: 'bg-purple-100 text-purple-700',
    blue: 'bg-blue-100 text-blue-600',
    amber: 'bg-amber-100 text-amber-700',
  }
  return <span className={cn(pill, map[tone])}>{children}</span>
}

export function PlanStatusBadge({ status }: { status: PlanStatus | SubscriptionStatus }) {
  return <GenericBadge tone={status === 'ACTIVE' ? 'green' : status === 'EXPIRED' ? 'red' : 'gray'}>{status.charAt(0) + status.slice(1).toLowerCase()}</GenericBadge>
}
