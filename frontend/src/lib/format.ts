const dateFmt = new Intl.DateTimeFormat('en-US', { month: 'short', day: '2-digit' })
const longDateFmt = new Intl.DateTimeFormat('en-US', { month: 'long', day: 'numeric', year: 'numeric' })

export const formatShortDate = (iso: string | Date) => dateFmt.format(new Date(iso))
export const formatLongDate = (d: Date = new Date()) => longDateFmt.format(d)
const monthYearFmt = new Intl.DateTimeFormat('en-US', { month: 'short', year: 'numeric' })
export const formatMonthYear = (iso: string | Date) => monthYearFmt.format(new Date(iso))

export function timeAgo(iso: string | Date): string {
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  return `${d}d ago`
}

export const initials = (first?: string, last?: string) =>
  `${(first ?? '').charAt(0)}${(last ?? '').charAt(0)}`.toUpperCase() || '?'

export const shortName = (first?: string, last?: string) =>
  first ? `${first} ${(last ?? '').charAt(0)}${last ? '.' : ''}`.trim() : 'Unassigned'

export const minutesToHuman = (min: number) => {
  if (min % 1440 === 0) return `${min / 1440}d`
  if (min % 60 === 0) return `${min / 60}h`
  return `${min}m`
}

/** Human file size from a byte count: 0 B, 512 KB, 1.4 MB, 2.1 GB. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) { value /= 1024; i++ }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`
}

/**
 * Formats a bare `yyyy-MM-dd` from the backend. Parsed from its parts, not `new Date(iso)`:
 * that reads a date-only string as UTC midnight, which renders as the previous day for anyone
 * west of Greenwich. The same reason DateRangePicker has its own isoDate().
 */
export function formatDateOnly(iso: string | undefined | null): string {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  return longDateFmt.format(new Date(y, m - 1, d))
}

/** Same, but compact: "10 Sep 2026" → "Sep 10". */
export function formatDateOnlyShort(iso: string | undefined | null): string {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  return dateFmt.format(new Date(y, m - 1, d))
}

/** Today as `yyyy-MM-dd` in the browser's own timezone — safe to send as a LocalDate. */
export function todayIso(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** `yyyy-MM-dd` n months from an ISO date, clamped the way the backend's plusMonths does. */
export function addMonthsIso(iso: string, months: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  const target = new Date(y, m - 1 + months, 1)
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(d, lastDay))
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${target.getFullYear()}-${pad(target.getMonth() + 1)}-${pad(target.getDate())}`
}
