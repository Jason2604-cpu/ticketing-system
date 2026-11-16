import type { SlaState, TicketPriority, TicketStatus } from '../../types'

export const priorityStyles: Record<TicketPriority, { pill: string; dot: string; label: string }> = {
  CRITICAL: { pill: 'bg-red-100 text-red-600', dot: '#ef4444', label: 'Critical' },
  HIGH: { pill: 'bg-orange-100 text-orange-600', dot: '#f97316', label: 'High' },
  MEDIUM: { pill: 'bg-yellow-100 text-yellow-700', dot: '#eab308', label: 'Medium' },
  LOW: { pill: 'bg-green-100 text-green-600', dot: '#22c55e', label: 'Low' },
}

export const statusStyles: Record<TicketStatus, { pill: string; label: string }> = {
  OPEN: { pill: 'bg-blue-100 text-blue-600', label: 'Open' },
  IN_PROGRESS: { pill: 'bg-orange-100 text-orange-600', label: 'In Progress' },
  RESOLVED: { pill: 'bg-green-100 text-green-600', label: 'Resolved' },
  CLOSED: { pill: 'bg-gray-100 text-gray-600', label: 'Closed' },
}


/**
 * SLA standing. ON_TRACK is deliberately quiet — a ticket meeting its target is the normal case
 * and shouldn't compete for attention with the ones that aren't.
 */
export const slaStyles: Record<SlaState, { pill: string; label: string } | null> = {
  NONE: null,
  MET: { pill: 'bg-green-100 text-green-600', label: 'SLA met' },
  ON_TRACK: { pill: 'bg-gray-100 text-gray-500', label: 'On track' },
  AT_RISK: { pill: 'bg-amber-100 text-amber-700', label: 'At risk' },
  BREACHED: { pill: 'bg-red-100 text-red-600', label: 'SLA breached' },
}
