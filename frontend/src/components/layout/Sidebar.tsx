import { NavLink, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { LayoutGrid, ListChecks, BarChart3, Building2, ChevronDown, Settings, Ticket, LogOut, Shield, Plus, Users, KeyRound } from 'lucide-react'
import { cn } from '../../lib/cn'
import { useAuth } from '../../auth/useAuth'
import { companiesApi } from '../../api/companies'
import { usersApi } from '../../api/users'
import { ticketsApi } from '../../api/tickets'
import { SectionTitle, ProgressBar } from '../ui/Misc'
import { Avatar } from '../ui/Avatar'
import { api } from '../../lib/api'
import type { Role } from '../../types'

import { portalFor } from './portal'

const navItem = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center gap-3 rounded-lg px-3 py-2.5 text-[15px] font-medium transition-colors',
    isActive ? 'bg-brand-50 text-brand-600' : 'text-gray-700 hover:bg-gray-50',
  )

const Count = ({ n, active }: { n: number; active?: boolean }) =>
  n > 0 ? <span className={cn('ml-auto rounded-full px-2 py-0.5 text-xs font-semibold', active ? 'bg-brand-100 text-brand-700' : 'bg-gray-100 text-gray-600')}>{n}</span> : null

export function Sidebar() {
  const { user, logout } = useAuth()
  const portal = portalFor(user?.role)

  const tickets = useQuery({ queryKey: ['tickets', 'all'], queryFn: () => ticketsApi.list() })
  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list, enabled: portal !== 'client' })
  const allUsers = useQuery({ queryKey: ['users', 'all'], queryFn: () => usersApi.list(), enabled: portal === 'admin' })
  const myCompany = useQuery({ queryKey: ['companies', user?.companyId], queryFn: () => companiesApi.get(user!.companyId!), enabled: portal === 'client' && !!user?.companyId })

  const openCount = tickets.data?.filter((t) => (t.status === 'OPEN' || t.status === 'IN_PROGRESS') && (portal !== 'client' || t.companyId === user?.companyId)).length ?? 0
  const countByCompany = (name: string) => tickets.data?.filter((t) => t.companyName === name && t.status !== 'CLOSED').length ?? 0

  const brand = {
    admin: { icon: <Shield size={20} />, sub: 'Admin Panel' },
    engineer: { icon: <Ticket size={20} />, sub: 'Engineer Portal' },
    client: { icon: <Ticket size={20} />, sub: 'Client Portal' },
  }[portal]

  const seats = myCompany.data?.subscription
  const seatsUsed = seats?.usage.activeUsers ?? 0
  const seatsMax = seats?.limits.maxUsers ?? 0

  return (
    <aside className="flex h-full w-[272px] shrink-0 flex-col border-r border-line bg-white">
      <div className="flex items-center gap-3 border-b border-line px-5 py-5">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-600 text-white">{brand.icon}</div>
        <div>
          <p className="text-lg font-bold leading-tight">Konoha HelpDesk</p>
          <p className={cn('text-sm', portal === 'admin' ? 'font-medium text-brand-600' : 'text-muted')}>{brand.sub}</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4">
        {portal === 'admin' && (
          <div className="space-y-1">
            <NavLink to="/" end className={navItem}><LayoutGrid size={18} /> Overview</NavLink>
            <NavLink to="/admin/users" className={navItem}>{({ isActive }) => <><Users size={18} /> Users <Count n={allUsers.data?.length ?? 0} active={isActive} /></>}</NavLink>
            <NavLink to="/companies" className={navItem}>{({ isActive }) => <><Building2 size={18} /> Companies <Count n={companies.data?.length ?? 0} active={isActive} /></>}</NavLink>
            <NavLink to="/tickets" className={navItem}>{({ isActive }) => <><ListChecks size={18} /> All Tickets <Count n={openCount} active={isActive} /></>}</NavLink>
            <NavLink to="/analytics" className={navItem}><BarChart3 size={18} /> Analytics</NavLink>
            <NavLink to="/admin/settings" className={navItem}><Settings size={18} /> Admin Settings</NavLink>
            <NavLink to="/admin/roles" className={navItem}><KeyRound size={18} /> Role Management</NavLink>
          </div>
        )}

        {portal === 'engineer' && (
          <>
            <div className="space-y-1">
              <NavLink to="/" end className={navItem}><LayoutGrid size={18} /> Dashboard</NavLink>
              <NavLink to="/tickets" className={navItem}>{({ isActive }) => <><ListChecks size={18} /> All Tickets <Count n={openCount} active={isActive} /></>}</NavLink>
              <NavLink to="/analytics" className={navItem}><BarChart3 size={18} /> Analytics</NavLink>
            </div>
            <div className="mt-6">
              <div className="flex items-center justify-between px-3 pb-2">
                <SectionTitle>Companies</SectionTitle>
                <span className="flex items-center gap-1 text-xs text-muted"><span className="rounded-full bg-gray-100 px-1.5">{companies.data?.length ?? 0}</span><ChevronDown size={14} /></span>
              </div>
              <div className="space-y-0.5">
                {companies.data?.slice(0, 5).map((c) => (
                  <NavLink key={c.id} to={`/companies/${c.id}`} className={({ isActive }) => cn(navItem({ isActive }), 'py-2 text-sm')}>
                    <span className="flex h-6 w-6 items-center justify-center rounded-md bg-brand-100 text-brand-600"><Building2 size={13} /></span>
                    <span className="truncate">{c.name}</span>
                    <span className="ml-auto text-xs text-muted">{countByCompany(c.name)}</span>
                  </NavLink>
                ))}
              </div>
              <Link to="/companies" className="mt-2 block px-3 text-sm font-medium text-brand-600 hover:underline">View all {companies.data?.length ?? ''} →</Link>
            </div>
          </>
        )}

        {portal === 'client' && (
          <>
            <div className="space-y-1">
              <NavLink to="/" end className={navItem}><LayoutGrid size={18} /> Overview</NavLink>
              <NavLink to="/tickets" className={navItem}>{({ isActive }) => <><ListChecks size={18} /> My Tickets <Count n={openCount} active={isActive} /></>}</NavLink>
              <NavLink to="/tickets/new" className={navItem}><Plus size={18} /> New Ticket</NavLink>
              <NavLink to="/analytics" className={navItem}><BarChart3 size={18} /> Analytics</NavLink>
              <NavLink to="/settings" className={navItem}><Settings size={18} /> Settings</NavLink>
            </div>
            {user?.role === 'CUSTOMER_ADMIN' && seats && (
              <div className="mt-6 rounded-xl border border-brand-200 bg-brand-50 p-4">
                <div className="flex justify-between text-sm font-semibold text-brand-700"><span>Team Seats</span><span>{seatsUsed}/{seatsMax}</span></div>
                <ProgressBar value={seatsUsed} max={seatsMax} color="#111827" className="mt-2" />
                <p className="mt-2 text-sm text-brand-600">{Math.max(0, seatsMax - seatsUsed)} seats remaining</p>
              </div>
            )}
          </>
        )}
      </nav>

      <div className="border-t border-line p-3">
        {portal === 'client' && myCompany.data && (
          <div className="mb-2 rounded-lg bg-gray-50 px-3 py-2.5">
            <p className="flex items-center gap-2 text-sm font-semibold"><Building2 size={14} className="text-brand-600" /> {myCompany.data.name}</p>
            <p className="text-xs text-muted">{seats ? (seats.subscriptionType === 'CUSTOM' ? 'Custom Plan' : `${seats.planName} Plan`) : ''}</p>
          </div>
        )}
        <Link to={portal === 'client' ? '/settings' : '/profile'} className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-gray-50">
          <Avatar firstName={user?.firstName} lastName={user?.lastName} src={api.mediaUrl(user?.photoUrl)} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{user?.firstName} {user?.lastName}</p>
            <p className={cn('truncate text-xs', portal === 'admin' ? 'text-brand-600' : 'text-muted')}>{roleLabel(user?.role)}</p>
          </div>
          <button onClick={(e) => { e.preventDefault(); void logout() }} className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" aria-label="Sign out"><LogOut size={16} /></button>
        </Link>
      </div>
    </aside>
  )
}

function roleLabel(role?: Role) {
  switch (role) {
    case 'ADMIN': return 'Service Provider'
    case 'ENGINEER': return 'Engineer'
    case 'CUSTOMER_ADMIN': return 'Company Admin'
    case 'CUSTOMER': return 'Member'
    default: return ''
  }
}
