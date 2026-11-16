import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { Card } from './Card'

type Tone = 'brand' | 'blue' | 'orange' | 'green' | 'red' | 'yellow'
const tones: Record<Tone, string> = {
  brand: 'bg-brand-100 text-brand-600',
  blue: 'bg-blue-100 text-blue-600',
  orange: 'bg-orange-100 text-orange-500',
  green: 'bg-green-100 text-green-600',
  red: 'bg-red-100 text-red-500',
  yellow: 'bg-yellow-100 text-yellow-600',
}

export function StatCard({ label, value, sub, subTone = 'muted', icon, tone = 'brand', className }: {
  label: string
  value: ReactNode
  sub?: ReactNode
  subTone?: 'muted' | 'up' | 'down'
  icon?: ReactNode
  tone?: Tone
  className?: string
}) {
  return (
    <Card className={cn('p-5', className)}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-muted">{label}</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-ink">{value}</p>
        </div>
        {icon && <div className={cn('flex h-10 w-10 items-center justify-center rounded-full', tones[tone])}>{icon}</div>}
      </div>
      {sub && (
        <p className={cn('mt-3 text-sm', subTone === 'up' ? 'text-green-600' : subTone === 'down' ? 'text-red-500' : 'text-muted')}>{sub}</p>
      )}
    </Card>
  )
}

/** Compact centred metric, like the company page row. */
export function MiniStat({ label, value, className }: { label: string; value: ReactNode; className?: string }) {
  return (
    <Card className="px-4 py-5 text-center">
      <p className="text-sm text-muted">{label}</p>
      <p className={cn('mt-2 text-2xl font-semibold', className)}>{value}</p>
    </Card>
  )
}
