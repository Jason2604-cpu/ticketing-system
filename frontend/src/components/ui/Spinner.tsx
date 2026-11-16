import { cn } from '../../lib/cn'

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={cn('inline-block h-5 w-5 animate-spin rounded-full border-2 border-brand-200 border-t-brand-600', className)}
      aria-label="Loading"
    />
  )
}

export function FullPageSpinner() {
  return (
    <div className="flex h-full min-h-screen items-center justify-center">
      <Spinner className="h-8 w-8" />
    </div>
  )
}
