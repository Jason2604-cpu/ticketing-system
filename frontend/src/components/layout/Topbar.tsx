import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Search } from 'lucide-react'
import { formatLongDate } from '../../lib/format'
import { Button } from '../ui/Button'
import { NewTicketModal } from '../../pages/tickets/NewTicketModal'
import { NotificationBell } from './NotificationBell'
import { ThemeToggle } from './ThemeToggle'
import { useAuth } from '../../auth/useAuth'

export function Topbar({ title, subtitle, actions, search = true, newTicket = true }: { title: string; subtitle?: string; actions?: ReactNode; search?: boolean; newTicket?: boolean }) {
  const navigate = useNavigate()
  const { user, hasRole } = useAuth()
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const isClient = hasRole('CUSTOMER', 'CUSTOMER_ADMIN')
  // Default-role employees have no ticket access, so no ticket search or New Ticket for them.
  const ticketsAllowed = !hasRole('STAFF')
  const defaultSubtitle = isClient && user?.companyName ? `${user.companyName} · ${formatLongDate()}` : formatLongDate()

  return (
    <header className="flex items-center justify-between border-b border-line bg-surface px-8 py-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <p className="text-sm text-muted">{subtitle ?? defaultSubtitle}</p>
      </div>
      <div className="flex items-center gap-3">
        {search && ticketsAllowed && <form
          onSubmit={(e) => { e.preventDefault(); navigate(`/tickets?q=${encodeURIComponent(q)}`) }}
          className="relative hidden md:block"
        >
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search tickets..."
            className="h-10 w-60 rounded-lg border border-line bg-surface pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
          />
        </form>}
        <ThemeToggle />
        <NotificationBell />
        {actions}
        {newTicket && ticketsAllowed && <Button onClick={() => (isClient ? navigate('/tickets/new') : setOpen(true))}><Plus size={16} /> New Ticket</Button>}
      </div>
      {!isClient && ticketsAllowed && <NewTicketModal open={open} onClose={() => setOpen(false)} />}
    </header>
  )
}
