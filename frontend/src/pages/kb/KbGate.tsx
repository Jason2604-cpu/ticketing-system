import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { useKbAccess, type KbBase } from './kbAccess'

/**
 * One Knowledge Base, two addresses: staff read on /kb, customers on /help. A link written for one
 * side (an article linking /help/vpn-reset, a staff member opening a customer's link) is sent to
 * the same page on the other side instead of a dead end. Anyone with no Knowledge Base access —
 * a Default employee without the privilege — goes home. Whether a customer's plan includes it is
 * the page's business (it shows an "not in your plan" state), not a redirect.
 */
export function KbGate({ base }: { base: KbBase }) {
  const { user } = useAuth()
  const { isCustomer, isStaffReader } = useKbAccess()
  const location = useLocation()
  if (!user) return null
  const target: KbBase | null = isCustomer ? '/help' : isStaffReader ? '/kb' : null
  if (!target) return <Navigate to="/" replace />
  if (target !== base) return <Navigate to={target + location.pathname.slice(base.length) + location.search} replace />
  return <Outlet />
}
