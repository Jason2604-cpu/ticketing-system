import type { Role } from '../../types'

/** staff = an employee with the Default role: only the pages their custom roles open. */
export type Portal = 'admin' | 'engineer' | 'staff' | 'client'
export const portalFor = (role?: Role): Portal =>
  role === 'ADMIN' ? 'admin' : role === 'ENGINEER' ? 'engineer' : role === 'STAFF' ? 'staff' : 'client'
