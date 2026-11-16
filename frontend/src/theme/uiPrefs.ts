import { createContext, useContext } from 'react'

export type ThemeChoice = 'light' | 'dark' | 'system'
/** What's actually painted, once 'system' has been resolved against the OS. */
export type ResolvedTheme = 'light' | 'dark'

export interface UiPrefs {
  theme: ThemeChoice
  resolvedTheme: ResolvedTheme
  setTheme: (t: ThemeChoice) => void
  /** Cycles light → dark → system. */
  cycleTheme: () => void
  sidebarCollapsed: boolean
  toggleSidebar: () => void
}

export const UiPrefsContext = createContext<UiPrefs | undefined>(undefined)

export function useUiPrefs(): UiPrefs {
  const ctx = useContext(UiPrefsContext)
  if (!ctx) throw new Error('useUiPrefs must be used inside <UiPrefsProvider>')
  return ctx
}

export const THEME_KEY = 'konoha.theme'
export const SIDEBAR_KEY = 'konoha.sidebarCollapsed'

/**
 * Reads a stored preference, tolerating a browser that refuses storage entirely
 * (private mode, blocked site data). Never throws — a chrome preference must not
 * be able to stop the app from rendering.
 */
export function readStored<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
  try {
    const v = localStorage.getItem(key)
    return (allowed as readonly string[]).includes(v ?? '') ? (v as T) : fallback
  } catch {
    return fallback
  }
}

export function writeStored(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* preference is cosmetic; losing it is fine */ }
}
