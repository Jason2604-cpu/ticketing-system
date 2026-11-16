import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown, X } from 'lucide-react'
import { cn } from '../../lib/cn'

export interface MultiSelectOption<T extends string | number> {
  value: T
  label: string
  description?: string
  disabled?: boolean
}

/**
 * SelectMenu's sibling for "any number of these": the popup stays open as options are toggled,
 * picks show as removable chips, and an empty selection is a meaningful state rather than a
 * missing one — which is why {@code emptyLabel} is a real label ("All companies") and not a
 * greyed-out placeholder.
 */
export function MultiSelectMenu<T extends string | number>({
  values, onChange, options, emptyLabel = 'Any', className, disabled, invalid, searchable = true, searchPlaceholder = 'Search…',
}: {
  values: T[]
  onChange: (v: T[]) => void
  options: MultiSelectOption<T>[]
  /** Shown on the trigger when nothing is selected. Say what "nothing" MEANS here. */
  emptyLabel?: string
  className?: string
  disabled?: boolean
  invalid?: boolean
  searchable?: boolean
  searchPlaceholder?: string
}) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    // Capture phase: Modal stops mousedown from bubbling (so a click inside it doesn't close it),
    // which would otherwise also hide every outside-click from this listener.
    document.addEventListener('mousedown', onDoc, true)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDoc, true); document.removeEventListener('keydown', onKey) }
  }, [open])

  useEffect(() => { if (!open) setQuery('') }, [open])

  const selected = options.filter((o) => values.includes(o.value))
  const visible = query.trim()
    ? options.filter((o) => o.label.toLowerCase().includes(query.trim().toLowerCase()))
    : options

  const toggle = (v: T) =>
    onChange(values.includes(v) ? values.filter((x) => x !== v) : [...values, v])

  // Every handler calls preventDefault: this control is normally inside a Field, which is a real
  // <label>, and a click on a label's descendant forwards a second activation to the first
  // labelable element inside — the trigger button. Without this, toggling an option reopens or
  // closes the popup at random. Same reason SelectMenu does it.
  const stop = (fn: () => void) => (e: React.MouseEvent) => { e.preventDefault(); e.stopPropagation(); fn() }

  return (
    <div ref={ref} className={cn('relative', className)}>
      <button
        type="button"
        disabled={disabled}
        onClick={stop(() => setOpen((o) => !o))}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={cn(
          'flex w-full items-center gap-2 rounded-lg border bg-surface px-3 py-1.5 text-left text-sm focus:outline-none focus:ring-2 focus:ring-brand-100 disabled:bg-gray-50 disabled:text-gray-500',
          'min-h-10',
          invalid ? 'border-red-400' : open ? 'border-brand-500' : 'border-line',
        )}
      >
        <span className="flex flex-1 flex-wrap items-center gap-1 py-0.5">
          {selected.length === 0 ? (
            <span className="text-gray-400">{emptyLabel}</span>
          ) : (
            selected.map((o) => (
              <span key={String(o.value)} className="inline-flex max-w-full items-center gap-1 rounded-md bg-brand-50 px-1.5 py-0.5 text-xs font-medium text-brand-700">
                <span className="truncate">{o.label}</span>
                <span
                  role="button"
                  tabIndex={-1}
                  aria-label={`Remove ${o.label}`}
                  onClick={stop(() => toggle(o.value))}
                  className="rounded p-0.5 hover:bg-brand-100"
                >
                  <X size={11} />
                </span>
              </span>
            ))
          )}
        </span>
        <ChevronDown size={16} className={cn('shrink-0 text-muted transition-transform', open && 'rotate-180')} />
      </button>

      {open && (
        <div className="absolute left-0 right-0 z-30 mt-1 rounded-lg border border-line bg-surface p-1 shadow-lg">
          {searchable && options.length > 7 && (
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onClick={(e) => e.preventDefault()}
              placeholder={searchPlaceholder}
              className="mb-1 w-full rounded-md border border-line px-2.5 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
            />
          )}
          <ul role="listbox" aria-multiselectable className="max-h-64 overflow-y-auto">
            {visible.map((o) => {
              const active = values.includes(o.value)
              return (
                <li
                  key={String(o.value)}
                  role="option"
                  aria-selected={active}
                  onClick={stop(() => { if (!o.disabled) toggle(o.value) })}
                  className={cn(
                    'flex cursor-pointer items-center gap-2 rounded-md px-2.5 py-2 text-sm hover:bg-brand-50',
                    o.disabled && 'cursor-not-allowed opacity-50',
                    active && 'font-medium text-brand-700',
                  )}
                >
                  <span className={cn('flex h-4 w-4 shrink-0 items-center justify-center rounded border', active ? 'border-brand-600 bg-brand-600 text-white' : 'border-line')}>
                    {active && <Check size={11} />}
                  </span>
                  <span className="flex-1">
                    <span className="block truncate">{o.label}</span>
                    {o.description && <span className="block text-xs font-normal text-muted">{o.description}</span>}
                  </span>
                </li>
              )
            })}
            {visible.length === 0 && <li className="px-2.5 py-2 text-sm text-muted">Nothing matches</li>}
          </ul>
          {selected.length > 0 && (
            <button
              type="button"
              onClick={stop(() => onChange([]))}
              className="mt-1 w-full rounded-md px-2.5 py-1.5 text-left text-xs font-medium text-brand-600 hover:bg-gray-50"
            >
              Clear selection — {emptyLabel.toLowerCase()}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
