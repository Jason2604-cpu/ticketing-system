import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'

export function Table({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('overflow-x-auto', className)}>
      <table className="w-full min-w-[720px] text-sm">{children}</table>
    </div>
  )
}
export const Th = ({ children, className }: { children?: ReactNode; className?: string }) => (
  <th className={cn('px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-muted', className)}>{children}</th>
)
export const Td = ({ children, className, onClick }: { children?: ReactNode; className?: string; onClick?: (e: React.MouseEvent<HTMLTableCellElement>) => void }) => (
  <td onClick={onClick} className={cn('px-4 py-3.5 align-middle', className)}>{children}</td>
)
export const Tr = ({ children, className, onClick }: { children: ReactNode; className?: string; onClick?: () => void }) => (
  <tr onClick={onClick} className={cn('border-t border-line', onClick && 'cursor-pointer hover:bg-gray-50', className)}>{children}</tr>
)
