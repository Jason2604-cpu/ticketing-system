import { createElement } from 'react'
import { priorityStyles } from '../../components/ui/badgeStyles'
import type { HoldReason, TicketCategory, TicketPriority } from '../../types'

export const CATEGORY_OPTS = [
  { value: 'TECHNICAL', label: 'Technical', description: 'Bugs, errors, outages' },
  { value: 'BILLING', label: 'Billing', description: 'Invoices and payments' },
  { value: 'ACCOUNT', label: 'Account', description: 'Access, users, permissions' },
  { value: 'FEATURE', label: 'Feature request', description: 'Ideas and improvements' },
] as const satisfies readonly { value: TicketCategory; label: string; description: string }[]

export const PRIORITY_OPTS = (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as TicketPriority[]).map((p) => ({
  value: p, label: priorityStyles[p].label, icon: createElement('i', { className: 'inline-block h-2.5 w-2.5 rounded-full', style: { background: priorityStyles[p].dot } }),
}))


/** Mirrors the backend HoldReason enum (labels included, so a list can render without a ticket). */
export const HOLD_REASON_OPTS = [
  { value: 'SPARE_PART', label: 'Waiting for spare part', description: 'Part ordered from a supplier' },
  { value: 'VENDOR', label: 'Waiting for third-party vendor', description: 'Escalated to an ISP, principal or OEM' },
  { value: 'SCHEDULED', label: 'Scheduled with customer', description: 'A visit or session is booked' },
  { value: 'CUSTOMER_REPLY', label: 'Waiting for customer reply', description: 'Resumes by itself when the customer replies' },
  { value: 'OTHER', label: 'Other', description: 'Explain in the note' },
] as const satisfies readonly { value: HoldReason; label: string; description: string }[]

export const holdReasonLabel = (r?: HoldReason) => HOLD_REASON_OPTS.find((o) => o.value === r)?.label ?? 'On hold'
