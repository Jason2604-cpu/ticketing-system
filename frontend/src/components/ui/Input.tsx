import { forwardRef, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

const base =
  'w-full rounded-lg border border-line bg-surface px-3 text-sm text-ink placeholder:text-gray-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100 disabled:bg-gray-50 disabled:text-gray-500'

/**
 * Label + control + hint/error.
 *
 * NOTE FOR CUSTOM CONTROLS: this is a real <label>, so clicking anything inside it triggers label
 * activation and forwards a click to the first labelable element within — which for a popup control
 * is its own trigger button. Any control that renders an interactive layer inside a Field must call
 * preventDefault on those clicks (SelectMenu and MultiSelectMenu do). A plain input is unaffected,
 * and gets click-the-label-to-focus for free, which is why this stays a <label>.
 */
export function Field({ label, hint, error, children, className }: { label?: string; hint?: string; error?: string; children: ReactNode; className?: string }) {
  return (
    <label className={cn('block', className)}>
      {label && <span className="mb-1.5 block text-sm font-medium text-ink">{label}</span>}
      {children}
      {error ? <span className="mt-1 block text-xs text-red-600">{error}</span> : hint ? <span className="mt-1 block text-xs text-muted">{hint}</span> : null}
    </label>
  )
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(
  function Input({ className, invalid, ...rest }, ref) {
    return <input ref={ref} className={cn(base, 'h-10', invalid && 'border-red-400 focus:ring-red-100', className)} {...rest} />
  },
)

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea({ className, ...rest }, ref) {
  return <textarea ref={ref} className={cn(base, 'min-h-24 py-2', className)} {...rest} />
})

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select({ className, children, ...rest }, ref) {
  return (
    <select ref={ref} className={cn(base, 'h-10 appearance-none bg-[url("data:image/svg+xml;utf8,<svg xmlns=%27http://www.w3.org/2000/svg%27 width=%2712%27 height=%2712%27 viewBox=%270 0 24 24%27 fill=%27none%27 stroke=%27%236b7280%27 stroke-width=%272%27><path d=%27m6 9 6 6 6-6%27/></svg>")] bg-[length:12px] bg-[right_12px_center] bg-no-repeat pr-9', className)} {...rest}>
      {children}
    </select>
  )
})

export function Checkbox({ label, className, ...rest }: InputHTMLAttributes<HTMLInputElement> & { label?: ReactNode }) {
  return (
    <label className={cn('inline-flex cursor-pointer items-center gap-2 text-sm', rest.disabled && 'cursor-not-allowed opacity-60', className)}>
      <input type="checkbox" className="h-4 w-4 rounded border-line text-brand-600 focus:ring-brand-500" {...rest} />
      {label}
    </label>
  )
}
