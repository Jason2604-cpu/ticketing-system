import { Checkbox } from './Input'
import type { BusinessHours, DayOfWeek } from '../../types'

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Monday', TUESDAY: 'Tuesday', WEDNESDAY: 'Wednesday', THURSDAY: 'Thursday',
  FRIDAY: 'Friday', SATURDAY: 'Saturday', SUNDAY: 'Sunday',
}

const ORDER: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

/** Mon–Fri 09:00–18:00, weekend closed — matches the backend's BusinessHoursDto.defaults(). */
export const defaultBusinessHours = (): BusinessHours[] =>
  ORDER.map((day) => ({
    dayOfWeek: day,
    openTime: '09:00',
    closeTime: '18:00',
    closed: day === 'SATURDAY' || day === 'SUNDAY',
  }))

/** Total open hours in the week, for the summary line. */
const weeklyHours = (rows: BusinessHours[]) =>
  rows.reduce((sum, r) => {
    if (r.closed) return sum
    const [oh, om] = r.openTime.split(':').map(Number)
    const [ch, cm] = r.closeTime.split(':').map(Number)
    return sum + Math.max(0, (ch * 60 + cm) - (oh * 60 + om)) / 60
  }, 0)

/**
 * Seven-row weekly schedule that drives the SLA clock.
 *
 * A row with an opening time at or after its closing time is rejected by the API, so the input
 * is marked invalid here rather than letting the user find out on save.
 */
export function BusinessHoursEditor({ value, onChange, enabled, onEnabledChange }: {
  value: BusinessHours[]
  onChange: (rows: BusinessHours[]) => void
  enabled: boolean
  onEnabledChange: (v: boolean) => void
}) {
  const rows = ORDER.map((day) => value.find((r) => r.dayOfWeek === day)
    ?? { dayOfWeek: day, openTime: '09:00', closeTime: '18:00', closed: true })

  const set = (day: DayOfWeek, patch: Partial<BusinessHours>) =>
    onChange(rows.map((r) => (r.dayOfWeek === day ? { ...r, ...patch } : r)))

  const total = weeklyHours(rows)

  return (
    <div className="space-y-4">
      <Checkbox
        label="Pause the SLA clock outside business hours"
        checked={enabled}
        onChange={(e) => onEnabledChange(e.target.checked)}
      />
      <p className="-mt-2 text-sm text-muted">
        {enabled
          ? `SLA deadlines are counted in working time only — ${total.toFixed(total % 1 ? 1 : 0)} hours a week. A 4-hour target raised at 17:00 on Friday falls due on Monday morning.`
          : 'SLA deadlines run on a 24/7 wall clock, including nights and weekends.'}
      </p>

      <div className={enabled ? '' : 'pointer-events-none opacity-50'}>
        <div className="space-y-2">
          {rows.map((r) => {
            const invalid = !r.closed && r.openTime >= r.closeTime
            return (
              <div key={r.dayOfWeek} className="grid grid-cols-[110px_100px_1fr] items-center gap-3">
                <span className="text-sm font-medium">{DAY_LABELS[r.dayOfWeek]}</span>
                <Checkbox
                  label="Closed"
                  checked={r.closed}
                  onChange={(e) => set(r.dayOfWeek, { closed: e.target.checked })}
                />
                {r.closed ? (
                  <span className="text-sm text-muted">No SLA time accrues</span>
                ) : (
                  <div className="flex items-center gap-2">
                    <input
                      type="time"
                      value={r.openTime}
                      onChange={(e) => e.target.value && set(r.dayOfWeek, { openTime: e.target.value })}
                      className={`rounded-lg border px-2.5 py-1.5 text-sm ${invalid ? 'border-red-400' : 'border-line'}`}
                    />
                    <span className="text-sm text-muted">to</span>
                    <input
                      type="time"
                      value={r.closeTime}
                      onChange={(e) => e.target.value && set(r.dayOfWeek, { closeTime: e.target.value })}
                      className={`rounded-lg border px-2.5 py-1.5 text-sm ${invalid ? 'border-red-400' : 'border-line'}`}
                    />
                    {invalid && <span className="text-xs text-red-600">Opening must be before closing</span>}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

/** True when every open day has a sane window — call before submitting. */
export const businessHoursValid = (rows: BusinessHours[]) =>
  rows.every((r) => r.closed || r.openTime < r.closeTime)
