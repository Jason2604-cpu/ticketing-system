import { createContext } from 'react'
import type { Privilege, Role, User } from '../types'

export interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
  hasRole: (...roles: Role[]) => boolean
  /** Whether the user holds a privilege via any custom (Role Management) role granted to them. */
  hasPrivilege: (privilege: Privilege) => boolean
}

export const AuthContext = createContext<AuthState | undefined>(undefined)
