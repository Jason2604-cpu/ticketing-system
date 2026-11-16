import { Building2, CircleUserRound, Crown, KeyRound, User as UserIcon, Wrench, type LucideIcon } from 'lucide-react'
import type { BadgeTone } from '../components/ui/Badge'
import type { Role } from '../types'

/**
 * One definition of every role's label, icon and colour, used everywhere a role is shown
 * (user list badges, filters, the invite form, the edit dialog, Role Management, the sidebar),
 * so the same role never appears with two different icons.
 *
 * Employees hold exactly one of Admin / Engineer / Default (+ optional custom roles);
 * Admin includes everything an Engineer can do. Customers are Company Admin or Customer.
 */
export interface RoleMeta {
  label: string
  description: string
  icon: LucideIcon
  tone: BadgeTone
  side: 'staff' | 'customer'
}

export const ROLE_META: Record<Role, RoleMeta> = {
  ADMIN: { label: 'Admin', description: 'Full access to everything — includes all Engineer work', icon: Crown, tone: 'purple', side: 'staff' },
  ENGINEER: { label: 'Engineer', description: 'Works tickets for their assigned companies', icon: Wrench, tone: 'blue', side: 'staff' },
  STAFF: { label: 'Default', description: 'Employee who can sign in but has no module until a role is ticked', icon: CircleUserRound, tone: 'gray', side: 'staff' },
  CUSTOMER_ADMIN: { label: 'Company Admin', description: 'Customer who manages their company team', icon: Building2, tone: 'brand', side: 'customer' },
  CUSTOMER: { label: 'Customer', description: 'Raises and follows their company tickets', icon: UserIcon, tone: 'gray', side: 'customer' },
}

/** Display order: employees first (most access first), then the customer side. */
export const ROLE_ORDER: Role[] = ['ADMIN', 'ENGINEER', 'STAFF', 'CUSTOMER_ADMIN', 'CUSTOMER']

/** Custom (Role Management) roles all share this look. */
export const CUSTOM_ROLE_ICON = KeyRound
export const CUSTOM_ROLE_TONE: BadgeTone = 'amber'

export const roleLabel = (r: Role) => ROLE_META[r]?.label ?? r
export const isStaffRole = (r?: Role) => !!r && ROLE_META[r].side === 'staff'
export const isCustomerRole = (r?: Role) => !!r && ROLE_META[r].side === 'customer'
