import { createElement } from 'react'
import { priorityStyles } from '../../components/ui/badgeStyles'
import type { TicketCategory, TicketPriority } from '../../types'

export const CATEGORY_OPTS = [
  { value: 'TECHNICAL', label: 'Technical', description: 'Bugs, errors, outages' },
  { value: 'BILLING', label: 'Billing', description: 'Invoices and payments' },
  { value: 'ACCOUNT', label: 'Account', description: 'Access, users, permissions' },
  { value: 'FEATURE', label: 'Feature request', description: 'Ideas and improvements' },
] as const satisfies readonly { value: TicketCategory; label: string; description: string }[]

export const PRIORITY_OPTS = (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as TicketPriority[]).map((p) => ({
  value: p, label: priorityStyles[p].label, icon: createElement('i', { className: 'inline-block h-2.5 w-2.5 rounded-full', style: { background: priorityStyles[p].dot } }),
}))

