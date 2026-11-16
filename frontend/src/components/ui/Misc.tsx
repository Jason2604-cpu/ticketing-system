import type { ReactNode } from 'react'
import { Inbox } from 'lucide-react'
import { cn } from '../../lib/cn'

export function EmptyState({ title, hint, action, icon }: { title: string; hint?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
      <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-gray-100 text-muted">{icon ?? <Inbox size={22} />}</div>
      <p className="font-medium text-ink">{title}</p>
      {hint && <p className="mt-1 max-w-sm text-sm text-muted">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Alert({ tone = 'error', children, className }: { tone?: 'error' | 'success' | 'info' | 'warning'; children: ReactNode; className?: string }) {
  const map = {
    error: 'border-red-200 bg-red-50 text-red-700',
    success: 'border-green-200 bg-green-50 text-green-700',
    info: 'border-blue-200 bg-blue-50 text-blue-700',
    warning: 'border-yellow-200 bg-yellow-50 text-yellow-800',
  }
  return <div className={cn('rounded-lg border px-4 py-3 text-sm', map[tone], className)}>{children}</div>
}

/**
 * `color` defaults to the brand accent. Pass `neutral` for a usage bar that
 * shouldn't carry a status meaning — it tracks the ink colour, so it inverts
 * with the theme instead of staying near-black on a dark page.
 */
export function ProgressBar({ value, max = 100, color, neutral = false, className, height = 'h-1.5' }: { value: number; max?: number; color?: string; neutral?: boolean; className?: string; height?: string }) {
  const fill = color ?? (neutral ? 'var(--ink)' : 'var(--accent-600)')
  const pct = Math.max(0, Math.min(100, (value / max) * 100))
  return (
    <div className={cn('w-full overflow-hidden rounded-full bg-gray-100', height, className)}>
      <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: fill }} />
    </div>
  )
}

export function SectionTitle({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cn('text-xs font-semibold uppercase tracking-wider text-muted', className)}>{children}</p>
}
