import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import { cn } from '../../lib/cn'

export interface SelectOption<T extends string | number> {
  value: T
  label: string
  description?: string
  icon?: ReactNode
  disabled?: boolean
}

/**
 * Styled replacement for <select>: a button that opens a popover list with
 * check marks, descriptions and keyboard support (↑ ↓ Enter Esc).
 */
export function SelectMenu<T extends string | number>({ value, onChange, options, placeholder = 'Select…', className, disabled, size = 'md', invalid }: {
  value: T | '' | undefined
  onChange: (v: T) => void
  options: SelectOption<T>[]
  placeholder?: string
  className?: string
  disabled?: boolean
  size?: 'sm' | 'md'
  invalid?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [hi, setHi] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const selected = options.find((o) => o.value === value)

  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    // Capture phase: Modal stops mousedown from bubbling (so a click inside it doesn't close it),
    // which would otherwise also hide every outside-click from this listener.
    document.addEventListener('mousedown', onDoc, true)
    return () => document.removeEventListener('mousedown', onDoc, true)
  }, [open])

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') { setOpen(false); return }
    if (!open && (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); setOpen(true); setHi(Math.max(0, options.findIndex((o) => o.value === value))); return }
    if (!open) return
    if (e.key === 'ArrowDown') { e.preventDefault(); setHi((h) => Math.min(options.length - 1, h + 1)) }
    if (e.key === 'ArrowUp') { e.preventDefault(); setHi((h) => Math.max(0, h - 1)) }
    if (e.key === 'Enter') { e.preventDefault(); const o = options[hi]; if (o && !o.disabled) { onChange(o.value); setOpen(false) } }
  }

  return (
    <div ref={ref} className={cn('relative', className)}>
      {/* preventDefault here too: inside a Field's <label>, a click on the trigger is also a click
          on the label, which would forward a second activation straight back to this button. */}
      <button type="button" disabled={disabled} onKeyDown={onKey} onClick={(e) => { e.preventDefault(); setOpen((o) => !o) }} aria-haspopup="listbox" aria-expanded={open}
        className={cn(
          'flex w-full items-center gap-2 rounded-lg border bg-surface text-left text-sm focus:outline-none focus:ring-2 focus:ring-brand-100 disabled:bg-gray-50 disabled:text-gray-500',
          size === 'sm' ? 'h-9 px-3' : 'h-10 px-3',
          invalid ? 'border-red-400' : open ? 'border-brand-500' : 'border-line',
        )}>
        {selected?.icon && <span className="shrink-0">{selected.icon}</span>}
        <span className={cn('flex-1 truncate', !selected && 'text-gray-400')}>{selected?.label ?? placeholder}</span>
        <ChevronDown size={16} className={cn('shrink-0 text-muted transition-transform', open && 'rotate-180')} />
      </button>
      {open && (
        <ul role="listbox" className="absolute left-0 right-0 z-30 mt-1 max-h-72 overflow-y-auto rounded-lg border border-line bg-surface p-1 shadow-lg">
          {options.map((o, i) => {
            const active = o.value === value
            return (
              <li key={String(o.value)} role="option" aria-selected={active} onMouseEnter={() => setHi(i)}
                onClick={(e) => {
                  // preventDefault is load-bearing, not tidiness. Field renders a <label>, so this
                  // popup usually sits INSIDE one. A click on any descendant of a label has label
                  // activation as its default action, which forwards a second click to the first
                  // labelable control in that label — the trigger button above. That re-fired its
                  // onClick toggle, so the menu closed here and instantly reopened, and picking an
                  // option looked like it did nothing.
                  e.preventDefault()
                  if (!o.disabled) { onChange(o.value); setOpen(false) }
                }}
                className={cn('flex cursor-pointer items-center gap-2 rounded-md px-2.5 py-2 text-sm', o.disabled && 'cursor-not-allowed opacity-50', i === hi && !o.disabled && 'bg-brand-50', active && 'font-medium text-brand-700')}>
                {o.icon && <span className="shrink-0">{o.icon}</span>}
                <span className="flex-1">
                  <span className="block">{o.label}</span>
                  {o.description && <span className="block text-xs font-normal text-muted">{o.description}</span>}
                </span>
                {active && <Check size={15} className="shrink-0 text-brand-600" />}
              </li>
            )
          })}
          {options.length === 0 && <li className="px-2.5 py-2 text-sm text-muted">No options</li>}
        </ul>
      )}
    </div>
  )
}
