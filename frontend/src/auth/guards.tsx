import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'
import { FullPageSpinner } from '../components/ui/Spinner'
import type { Privilege, Role } from '../types'

export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  // Signed in with a password they didn't choose (the default admin): the API refuses everything
  // else until it's replaced, so don't render the app shell at all.
  if (user.mustChangePassword && location.pathname !== '/change-password') return <Navigate to="/change-password" replace />
  return <Outlet />
}

/** Primary role OR an additively-granted one (e.g. an Engineer granted Admin) unlocks the route. */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { hasRole, user } = useAuth()
  if (!user) return null
  return hasRole(...roles) ? <Outlet /> : <Navigate to="/" replace />
}

/** Same as RequireRole, but a custom-role privilege also unlocks it — e.g. Role Management itself. */
export function RequireRoleOrPrivilege({ roles, privilege }: { roles: Role[]; privilege: Privilege }) {
  const { hasRole, hasPrivilege, user } = useAuth()
  if (!user) return null
  return hasRole(...roles) || hasPrivilege(privilege) ? <Outlet /> : <Navigate to="/" replace />
}

export function RedirectIfAuthed() {
  const { user, loading } = useAuth()
  if (loading) return <FullPageSpinner />
  return user ? <Navigate to="/" replace /> : <Outlet />
}
