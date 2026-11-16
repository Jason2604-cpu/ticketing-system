import type { ContractFrequency, ContractState, ContractType, TicketCategory, TicketPriority, VisitStatus } from '../../types'

/** Mirrors ContractFrequency on the backend, including how many visits a 12-month term holds. */
export const FREQUENCIES: { value: ContractFrequency; label: string; months: number }[] = [
  { value: 'ONE_OFF', label: 'One-off', months: 0 },
  { value: 'MONTHLY', label: 'Monthly', months: 1 },
  { value: 'QUARTERLY', label: 'Quarterly', months: 3 },
  { value: 'SEMI_ANNUAL', label: 'Every 6 months', months: 6 },
  { value: 'ANNUAL', label: 'Annually', months: 12 },
]

export const frequencyMonths = (f: ContractFrequency) => FREQUENCIES.find((o) => o.value === f)?.months ?? 12
export const defaultOccurrences = (f: ContractFrequency) => {
  const m = frequencyMonths(f)
  return m === 0 ? 1 : 12 / m
}

/**
 * Not every contract is a calendar. A support retainer has a term, an owner and a renewal date but
 * nothing scheduled — the work arrives as tickets. Making one declare a frequency would put
 * fictional visits on the schedule and fictional overdue counts on the dashboard.
 */
export const CONTRACT_TYPES: { value: ContractType; label: string; description: string }[] = [
  { value: 'SCHEDULED_VISITS', label: 'Scheduled visits', description: 'Preventive maintenance, inspections, certifications — dated visits generated up front' },
  { value: 'TICKET_BASED', label: 'Ticket-based support', description: 'Retainers, block-of-hours, standby cover — no schedule; work arrives as tickets' },
]

export const STATE_LABEL: Record<ContractState, string> = {
  ACTIVE: 'Active',
  EXPIRING_SOON: 'Expiring soon',
  EXPIRED: 'Expired',
  CANCELLED: 'Cancelled',
}

export const STATE_TONE: Record<ContractState, 'green' | 'red' | 'gray' | 'brand'> = {
  ACTIVE: 'green',
  EXPIRING_SOON: 'brand',
  EXPIRED: 'red',
  CANCELLED: 'gray',
}

export const VISIT_LABEL: Record<VisitStatus, string> = {
  SCHEDULED: 'Scheduled',
  COMPLETED: 'Completed',
  SKIPPED: 'Skipped',
}

export const PRIORITIES: { value: TicketPriority; label: string }[] = [
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
]

export const CATEGORIES: { value: TicketCategory; label: string }[] = [
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'BILLING', label: 'Billing' },
  { value: 'ACCOUNT', label: 'Account' },
  { value: 'FEATURE', label: 'Feature' },
]

/** "in 34 days" / "12 days ago" / "today", from the server-computed daysToExpiry. */
export function expiryPhrase(days: number): string {
  if (days === 0) return 'today'
  if (days > 0) return `in ${days} day${days === 1 ? '' : 's'}`
  const past = Math.abs(days)
  return `${past} day${past === 1 ? '' : 's'} ago`
}
