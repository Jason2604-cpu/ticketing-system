import { cn } from '../../lib/cn'
import { initials } from '../../lib/format'

const palette = ['bg-brand-100 text-brand-700', 'bg-blue-100 text-blue-700', 'bg-green-100 text-green-700', 'bg-orange-100 text-orange-700', 'bg-purple-100 text-purple-700']

export function Avatar({ firstName, lastName, src, size = 'md', className }: {
  firstName?: string
  lastName?: string
  src?: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
  className?: string
}) {
  const sizes = { sm: 'h-7 w-7 text-[11px]', md: 'h-9 w-9 text-xs', lg: 'h-12 w-12 text-sm', xl: 'h-20 w-20 text-2xl' }
  const key = ((firstName ?? '').charCodeAt(0) || 0) % palette.length
  if (src) return <img src={src} alt="" className={cn('rounded-full object-cover', sizes[size], className)} />
  return (
    <span className={cn('inline-flex shrink-0 items-center justify-center rounded-full font-semibold', sizes[size], palette[key], className)}>
      {initials(firstName, lastName)}
    </span>
  )
}

export function CompanyMark({ name, src, size = 'md', className }: { name: string; src?: string; size?: 'sm' | 'md' | 'lg' | 'xl'; className?: string }) {
  const sizes = { sm: 'h-6 w-6 text-[11px] rounded-md', md: 'h-9 w-9 text-sm rounded-lg', lg: 'h-12 w-12 text-lg rounded-xl', xl: 'h-16 w-16 text-2xl rounded-2xl' }
  if (src) return <img src={src} alt="" className={cn('object-cover', sizes[size], className)} />
  return (
    <span className={cn('inline-flex shrink-0 items-center justify-center bg-brand-100 font-bold text-brand-600', sizes[size], className)}>
      {name.charAt(0).toUpperCase()}
    </span>
  )
}
