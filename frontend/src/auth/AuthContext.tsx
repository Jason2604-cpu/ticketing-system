import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { AuthContext } from './authState'
import type { Privilege, Role, User } from '../types'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setUser(await authApi.me())
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    authApi.me().then((u) => { if (!cancelled) setUser(u) }).catch(() => { if (!cancelled) setUser(null) }).finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    await authApi.login(email, password)
    setUser(await authApi.me())
  }, [])

  const logout = useCallback(async () => {
    try { await authApi.logout() } finally { setUser(null) }
  }, [])

  // One role per user. (Employees used to be able to hold Engineer AND Admin; now Admin simply
  // includes everything an Engineer can do, so every check that lets engineers in lists ADMIN too.)
  const hasRole = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user])

  // Whether the user holds a given privilege via any custom (Role Management) role they've been granted.
  const hasPrivilege = useCallback((privilege: Privilege) => !!user?.customRoles?.some((r) => r.privileges.includes(privilege)), [user])

  const value = useMemo(() => ({ user, loading, login, logout, refresh, hasRole, hasPrivilege }), [user, loading, login, logout, refresh, hasRole, hasPrivilege])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
