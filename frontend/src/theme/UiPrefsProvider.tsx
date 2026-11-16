import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { SIDEBAR_KEY, THEME_KEY, UiPrefsContext, readStored, writeStored, type ResolvedTheme, type ThemeChoice } from './uiPrefs'

const prefersDark = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches

/**
 * Theme and sidebar state, persisted per browser.
 *
 * Deliberately localStorage and not the user's profile: these are per-device
 * chrome preferences. Someone on a bright office monitor and a dim laptop wants
 * different answers on each, and syncing them through the account would make
 * that impossible while also putting a write on the server for every toggle.
 *
 * 'system' is a real third state, not a synonym for the current OS setting — it
 * keeps following the OS if that changes while the app is open, which the media
 * query listener below handles.
 */
export function UiPrefsProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeChoice>(() =>
    readStored<ThemeChoice>(THEME_KEY, ['light', 'dark', 'system'], 'system'))
  const [systemDark, setSystemDark] = useState(prefersDark)
  const [sidebarCollapsed, setCollapsed] = useState(() =>
    readStored(SIDEBAR_KEY, ['true', 'false'], 'false') === 'true')

  const resolvedTheme: ResolvedTheme = theme === 'system' ? (systemDark ? 'dark' : 'light') : theme

  // Follow the OS while 'system' is selected.
  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)')
    if (!mq) return
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  // The attribute every token in index.css keys off.
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolvedTheme)
  }, [resolvedTheme])

  const setTheme = useCallback((t: ThemeChoice) => {
    setThemeState(t)
    writeStored(THEME_KEY, t)
    // Only the deliberate switch fades. The attribute is removed on a timer so
    // hovers and layout transitions don't inherit a colour fade for the rest of
    // the session.
    const root = document.documentElement
    root.setAttribute('data-theme-animating', '')
    window.setTimeout(() => root.removeAttribute('data-theme-animating'), 250)
  }, [])

  const cycleTheme = useCallback(() => {
    setTheme(theme === 'light' ? 'dark' : theme === 'dark' ? 'system' : 'light')
  }, [theme, setTheme])

  const toggleSidebar = useCallback(() => {
    setCollapsed((c) => {
      writeStored(SIDEBAR_KEY, String(!c))
      return !c
    })
  }, [])

  const value = useMemo(
    () => ({ theme, resolvedTheme, setTheme, cycleTheme, sidebarCollapsed, toggleSidebar }),
    [theme, resolvedTheme, setTheme, cycleTheme, sidebarCollapsed, toggleSidebar])

  return <UiPrefsContext.Provider value={value}>{children}</UiPrefsContext.Provider>
}
