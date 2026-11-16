import { useState } from 'react'
import { COUNTRIES, joinPhone, splitPhone, type Country } from '../../lib/countries'
import { SelectMenu } from './SelectMenu'
import { cn } from '../../lib/cn'

/**
 * Country selector + local number. `value` / `onChange` speak E.164 ("+60182876866"),
 * which is what the backend validates. The user only types the local part.
 */
export function PhoneInput({ value, onChange, invalid, className, disabled }: {
  value: string
  onChange: (e164: string) => void
  invalid?: boolean
  className?: string
  disabled?: boolean
}) {
  const initial = splitPhone(value)
  const [country, setCountry] = useState<Country>(initial.country)
  const [local, setLocal] = useState(initial.local)

  const update = (c: Country, l: string) => { setCountry(c); setLocal(l); onChange(joinPhone(c, l)) }

  return (
    <div className={cn('flex gap-2', className)}>
      <SelectMenu<string>
        className="w-40 shrink-0"
        value={country.code}
        disabled={disabled}
        onChange={(code) => update(COUNTRIES.find((c) => c.code === code)!, local)}
        options={COUNTRIES.map((c) => ({ value: c.code, label: `${c.flag} +${c.dial}`, description: c.name }))}
      />
      <div className={cn('flex h-10 flex-1 items-center rounded-lg border bg-surface focus-within:border-brand-500 focus-within:ring-2 focus-within:ring-brand-100', invalid ? 'border-red-400' : 'border-line', disabled && 'bg-gray-50')}>
        <span className="select-none pl-3 text-sm text-muted">+{country.dial}</span>
        <input
          type="tel" inputMode="numeric" disabled={disabled}
          value={local}
          onChange={(e) => update(country, e.target.value.replace(/[^0-9]/g, ''))}
          placeholder="182876866"
          className="h-full flex-1 bg-transparent px-2 text-sm focus:outline-none"
        />
      </div>
    </div>
  )
}
