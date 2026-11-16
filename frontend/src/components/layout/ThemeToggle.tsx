import { Monitor, Moon, Sun } from 'lucide-react'
import { useUiPrefs } from '../../theme/uiPrefs'

/**
 * Three states, not two: light, dark, and follow-the-system.
 *
 * "System" is worth the extra click because it is the only setting that keeps
 * being right — someone whose OS switches at sunset gets an app that switches
 * with it. It's also the default, so most people never touch this at all.
 *
 * One button that cycles rather than a menu: the whole control is a preference
 * you set once, and the current state is legible from the icon.
 */
export function ThemeToggle() {
  const { theme, resolvedTheme, cycleTheme } = useUiPrefs()

  const label =
    theme === 'system' ? `Theme: following your system (${resolvedTheme})`
    : theme === 'dark' ? 'Theme: dark'
    : 'Theme: light'

  const next = theme === 'light' ? 'dark' : theme === 'dark' ? 'system' : 'light'

  return (
    <button
      type="button"
      onClick={cycleTheme}
      title={`${label} — click for ${next}`}
      aria-label={`${label}. Switch to ${next}.`}
      className="flex h-10 w-10 items-center justify-center rounded-lg text-muted transition-colors hover:bg-gray-100 hover:text-ink"
    >
      {theme === 'system' ? <Monitor size={18} /> : theme === 'dark' ? <Moon size={18} /> : <Sun size={18} />}
    </button>
  )
}
