import { useState } from 'react'
import { Calendar } from 'lucide-react'
import type { ReportRange } from '../../types'

/** ISO yyyy-mm-dd in the browser's local timezone (toISOString would shift the day for UTC+8). */
export function isoDate(d: Date): string {
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}

const daysAgo = (n: number) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d
}

export const PRESETS: { label: string; range: () => ReportRange }[] = [
  { label: '7 days', range: () => ({ from: isoDate(daysAgo(6)), to: isoDate(new Date()) }) },
  { label: '30 days', range: () => ({ from: isoDate(daysAgo(29)), to: isoDate(new Date()) }) },
  { label: '3 months', range: () => ({ from: isoDate(daysAgo(89)), to: isoDate(new Date()) }) },
  { label: '12 months', range: () => ({ from: isoDate(daysAgo(364)), to: isoDate(new Date()) }) },
]

export const defaultRange = (): ReportRange => PRESETS[1].range()

/**
 * Preset buttons plus a custom from/to pair. Presets are the common case; the date inputs are
 * there for the one report someone needs over an exact billing period.
 */
export function DateRangePicker({ value, onChange }: { value: ReportRange; onChange: (r: ReportRange) => void }) {
  const [custom, setCustom] = useState(false)
  const today = isoDate(new Date())

  const activePreset = PRESETS.findIndex((p) => {
    const r = p.range()
    return r.from === value.from && r.to === value.to
  })

  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="flex overflow-hidden rounded-lg border border-line">
        {PRESETS.map((p, i) => (
          <button
            key={p.label}
            onClick={() => { setCustom(false); onChange(p.range()) }}
            className={`px-3 py-1.5 text-sm transition-colors ${!custom && activePreset === i ? 'bg-brand-500 text-white' : 'bg-surface text-muted hover:bg-gray-50'}`}
          >
            {p.label}
          </button>
        ))}
        <button
          onClick={() => setCustom(true)}
          className={`flex items-center gap-1.5 border-l border-line px-3 py-1.5 text-sm transition-colors ${custom || activePreset === -1 ? 'bg-brand-500 text-white' : 'bg-surface text-muted hover:bg-gray-50'}`}
        >
          <Calendar size={14} /> Custom
        </button>
      </div>

      {(custom || activePreset === -1) && (
        <div className="flex items-center gap-2 text-sm">
          <input
            type="date"
            value={value.from}
            max={value.to}
            onChange={(e) => e.target.value && onChange({ ...value, from: e.target.value })}
            className="rounded-lg border border-line px-2.5 py-1.5"
          />
          <span className="text-muted">to</span>
          <input
            type="date"
            value={value.to}
            min={value.from}
            max={today}
            onChange={(e) => e.target.value && onChange({ ...value, to: e.target.value })}
            className="rounded-lg border border-line px-2.5 py-1.5"
          />
        </div>
      )}
    </div>
  )
}
