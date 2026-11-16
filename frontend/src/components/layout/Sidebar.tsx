import { NavLink, Link, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { LayoutGrid, Layers, ListChecks, BarChart3, BookOpen, Building2, ChevronDown, LifeBuoy, Settings, Ticket, LogOut, Shield, Plus, Users, KeyRound, FileSignature, ScrollText, FileText, HardDrive, PanelLeftClose, PanelLeftOpen, Zap } from 'lucide-react'
import { cn } from '../../lib/cn'
import { useAuth } from '../../auth/useAuth'
import { useFeature } from '../../lib/useFeature'
import { useUiPrefs } from '../../theme/uiPrefs'
import { companiesApi } from '../../api/companies'
import { usersApi } from '../../api/users'
import { ticketsApi } from '../../api/tickets'
import { SectionTitle, ProgressBar } from '../ui/Misc'
import { Avatar } from '../ui/Avatar'
import { api } from '../../lib/api'
import type { Privilege, Role } from '../../types'

import { portalFor } from './portal'
import { useKbAccess, useKbRequestedCount } from '../../pages/kb/kbAccess'

/**
 * Collapsed, the nav is icons only. The label is still rendered but hidden with
 * `sr-only` rather than removed, so screen readers and the `title` tooltip keep
 * working — an icon rail with no accessible name is not a nav, it's a puzzle.
 */
const makeNavItem = (collapsed: boolean) => ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center rounded-lg text-[15px] font-medium transition-colors',
    collapsed ? 'justify-center px-0 py-2.5' : 'gap-3 px-3 py-2.5',
    isActive ? 'bg-brand-50 text-brand-600' : 'text-gray-700 hover:bg-gray-50',
  )

const Count = ({ n, active, hidden }: { n: number; active?: boolean; hidden?: boolean }) =>
  n > 0 && !hidden ? <span className={cn('ml-auto rounded-full px-2 py-0.5 text-xs font-semibold', active ? 'bg-brand-100 text-brand-700' : 'bg-gray-100 text-gray-600')}>{n}</span> : null

/** Wraps a nav label so it vanishes visually but stays in the accessibility tree. */
const L = ({ collapsed, children }: { collapsed: boolean; children: React.ReactNode }) =>
  collapsed ? <span className="sr-only">{children}</span> : <>{children}</>

export function Sidebar() {
  const { user, logout, hasPrivilege } = useAuth()
  const { sidebarCollapsed: collapsed, toggleSidebar } = useUiPrefs()
  const navItem = makeNavItem(collapsed)
  const portal = portalFor(user?.role)
  const hasReporting = useFeature('REPORTING')
  const hasAssets = useFeature('ASSET_MANAGEMENT')
  const hasKb = useFeature('KNOWLEDGE_BASE')
  const hasAutomation = useFeature('AUTOMATION_RULES')

  // Counts only — this used to fetch the entire ticket list (up to 500 full summary rows, each
  // carrying SLA state and a comment count) every 15 seconds on every page, to render two badges.
  const counts = useQuery({ queryKey: ['tickets', 'counts'], queryFn: ticketsApi.counts, enabled: portal !== 'staff' })
  // Reference data behind a badge count: it changes when an admin adds a company or invites
  // someone, not minute to minute, so it opts out of the app's 15s poll. The pages that actually
  // manage these still use their own queries and still see changes immediately after a mutation,
  // because those invalidate the same keys.
  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list, enabled: portal === 'admin' || portal === 'engineer', refetchInterval: false, staleTime: 5 * 60_000 })
  const allUsers = useQuery({ queryKey: ['users', 'all'], queryFn: () => usersApi.list(), enabled: portal === 'admin', refetchInterval: false, staleTime: 5 * 60_000 })
  const myCompany = useQuery({ queryKey: ['companies', user?.companyId], queryFn: () => companiesApi.get(user!.companyId!), enabled: portal === 'client' && !!user?.companyId })

  const openCount = counts.data?.total ?? 0
  // Keyed by company id now, not name — two customers with the same name used to share a badge.
  const countByCompany = (companyId: number) => counts.data?.[String(companyId)] ?? 0

  const brand = {
    admin: { icon: <Shield size={20} />, sub: 'Admin Panel' },
    engineer: { icon: <Ticket size={20} />, sub: 'Engineer Portal' },
    staff: { icon: <Ticket size={20} />, sub: 'Staff Portal' },
    client: { icon: <Ticket size={20} />, sub: 'Client Portal' },
  }[portal]

  const seats = myCompany.data?.subscription
  const seatsUsed = seats?.usage.activeUsers ?? 0
  const seatsMax = seats?.limits.maxUsers ?? 0

  return (
    <aside className={cn(
      'relative flex h-full shrink-0 flex-col border-r border-line bg-surface transition-[width] duration-200',
      collapsed ? 'w-[72px]' : 'w-[272px]',
    )}>
      <div className={cn('flex items-center border-b border-line py-5', collapsed ? 'justify-center px-3' : 'gap-3 px-5')}>
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white">{brand.icon}</div>
        {!collapsed && (
          <div className="min-w-0">
            <p className="truncate text-lg font-bold leading-tight">Konoha HelpDesk</p>
            <p className={cn('truncate text-sm', portal === 'admin' ? 'font-medium text-brand-600' : 'text-muted')}>{brand.sub}</p>
          </div>
        )}
      </div>

      {/*
        Sits on the edge rather than inside the header: at 72px there is no room
        for it beside the logo, and putting it on the border keeps it in the same
        place in both states so it never has to be hunted for.
      */}
      <button
        type="button"
        onClick={toggleSidebar}
        title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-expanded={!collapsed}
        className="absolute -right-3 top-16 z-20 flex h-6 w-6 items-center justify-center rounded-full border border-line bg-surface text-muted shadow-sm transition-colors hover:text-ink"
      >
        {collapsed ? <PanelLeftOpen size={13} /> : <PanelLeftClose size={13} />}
      </button>

      <nav className={cn('flex-1 overflow-y-auto py-4', collapsed ? 'px-2' : 'px-3')}>
        {portal === 'admin' && (
          <div className="space-y-1">
            <NavLink to="/" end title={collapsed ? 'Overview' : undefined} className={navItem}><LayoutGrid size={18} /> <L collapsed={collapsed}>Overview</L></NavLink>
            <NavLink to="/admin/users" title={collapsed ? 'Users' : undefined} className={navItem}>{({ isActive }) => <><Users size={18} /> <L collapsed={collapsed}>Users</L> <Count n={allUsers.data?.length ?? 0} active={isActive} hidden={collapsed} /></>}</NavLink>
            <NavLink to="/companies" title={collapsed ? 'Companies' : undefined} className={navItem}>{({ isActive }) => <><Building2 size={18} /> <L collapsed={collapsed}>Companies</L> <Count n={companies.data?.length ?? 0} active={isActive} hidden={collapsed} /></>}</NavLink>
            <NavLink to="/tickets" title={collapsed ? 'All Tickets' : undefined} className={navItem}>{({ isActive }) => <><ListChecks size={18} /> <L collapsed={collapsed}>All Tickets</L> <Count n={openCount} active={isActive} hidden={collapsed} /></>}</NavLink>
            <NavLink to="/analytics" title={collapsed ? 'Analytics' : undefined} className={navItem}><BarChart3 size={18} /> <L collapsed={collapsed}>Analytics</L></NavLink>
            <NavLink to="/automation" title={collapsed ? 'Automation' : undefined} className={navItem}><Zap size={18} /> <L collapsed={collapsed}>Automation</L></NavLink>
            <NavLink to="/admin/plans" title={collapsed ? 'Subscription Plans' : undefined} className={navItem}><Layers size={18} /> <L collapsed={collapsed}>Subscription Plans</L></NavLink>
            <NavLink to="/admin/contracts" title={collapsed ? 'Service Contracts' : undefined} className={navItem}><FileSignature size={18} /> <L collapsed={collapsed}>Service Contracts</L></NavLink>
            <NavLink to="/admin/assets" title={collapsed ? 'Assets' : undefined} className={navItem}><HardDrive size={18} /> <L collapsed={collapsed}>Assets</L></NavLink>
            <NavLink to="/admin/templates" title={collapsed ? 'Templates' : undefined} className={navItem}><FileText size={18} /> <L collapsed={collapsed}>Templates</L></NavLink>
            <KbNavLink collapsed={collapsed} navItem={navItem} />
            <NavLink to="/admin/roles" title={collapsed ? 'Role Management' : undefined} className={navItem}><KeyRound size={18} /> <L collapsed={collapsed}>Role Management</L></NavLink>
            <NavLink to="/admin/audit" title={collapsed ? 'Audit Log' : undefined} className={navItem}><ScrollText size={18} /> <L collapsed={collapsed}>Audit Log</L></NavLink>
          </div>
        )}

        {portal === 'engineer' && (
          <>
            <div className="space-y-1">
              <NavLink to="/" end title={collapsed ? 'Dashboard' : undefined} className={navItem}><LayoutGrid size={18} /> <L collapsed={collapsed}>Dashboard</L></NavLink>
              <NavLink to="/tickets" title={collapsed ? 'All Tickets' : undefined} className={navItem}>{({ isActive }) => <><ListChecks size={18} /> <L collapsed={collapsed}>All Tickets</L> <Count n={openCount} active={isActive} hidden={collapsed} /></>}</NavLink>
              <NavLink to="/analytics" title={collapsed ? 'Analytics' : undefined} className={navItem}><BarChart3 size={18} /> <L collapsed={collapsed}>Analytics</L></NavLink>
              <NavLink to="/automation" title={collapsed ? 'Automation' : undefined} className={navItem}><Zap size={18} /> <L collapsed={collapsed}>Automation</L></NavLink>
              <KbNavLink collapsed={collapsed} navItem={navItem} />
            </div>
            {!collapsed && <div className="mt-6">
              <div className="flex items-center justify-between px-3 pb-2">
                <SectionTitle>Companies</SectionTitle>
                <span className="flex items-center gap-1 text-xs text-muted"><span className="rounded-full bg-gray-100 px-1.5">{companies.data?.length ?? 0}</span><ChevronDown size={14} /></span>
              </div>
              <div className="space-y-0.5">
                {companies.data?.slice(0, 5).map((c) => (
                  <NavLink key={c.id} to={`/companies/${c.id}`} className={({ isActive }) => cn(navItem({ isActive }), 'py-2 text-sm')}>
                    <span className="flex h-6 w-6 items-center justify-center rounded-md bg-brand-100 text-brand-600"><Building2 size={13} /></span>
                    <span className="truncate">{c.name}</span>
                    <span className="ml-auto text-xs text-muted">{countByCompany(c.id)}</span>
                  </NavLink>
                ))}
              </div>
              <Link to="/companies" className="mt-2 block px-3 text-sm font-medium text-brand-600 hover:underline">View all {companies.data?.length ?? ''} →</Link>
            </div>}

            {/*
              Admin pages an engineer can only reach through a granted custom role. Without this
              the privilege was real but invisible — the route guard let them in, nothing linked
              there, so the only way to use it was to type the URL.
            */}
            <PrivilegedLinks collapsed={collapsed} navItem={navItem} hasPrivilege={hasPrivilege} />
          </>
        )}

        {portal === 'staff' && (
          <>
            {/* Default-role employee: home, plus exactly the pages their custom roles open. */}
            <div className="space-y-1">
              <NavLink to="/" end title={collapsed ? 'Home' : undefined} className={navItem}><LayoutGrid size={18} /> <L collapsed={collapsed}>Home</L></NavLink>
              {hasPrivilege('MANAGE_COMPANIES') && <NavLink to="/companies" title={collapsed ? 'Companies' : undefined} className={navItem}><Building2 size={18} /> <L collapsed={collapsed}>Companies</L></NavLink>}
            </div>
            <PrivilegedLinks collapsed={collapsed} navItem={navItem} hasPrivilege={hasPrivilege} />
          </>
        )}

        {portal === 'client' && (
          <>
            <div className="space-y-1">
              <NavLink to="/" end title={collapsed ? 'Overview' : undefined} className={navItem}><LayoutGrid size={18} /> <L collapsed={collapsed}>Overview</L></NavLink>
              <NavLink to="/tickets" title={collapsed ? 'My Tickets' : undefined} className={navItem}>{({ isActive }) => <><ListChecks size={18} /> <L collapsed={collapsed}>My Tickets</L> <Count n={openCount} active={isActive} hidden={collapsed} /></>}</NavLink>
              <NavLink to="/tickets/new" title={collapsed ? 'New Ticket' : undefined} className={navItem}><Plus size={18} /> <L collapsed={collapsed}>New Ticket</L></NavLink>
              {hasKb && <NavLink to="/help" title={collapsed ? 'Help Centre' : undefined} className={navItem}><LifeBuoy size={18} /> <L collapsed={collapsed}>Help Centre</L></NavLink>}
              {/* Both are paid features — hidden unless the company's plan carries them. The
                  backend gates them too; this only keeps dead links out of the nav. */}
              {hasAssets && <NavLink to="/assets" title={collapsed ? 'Our Equipment' : undefined} className={navItem}><HardDrive size={18} /> <L collapsed={collapsed}>Our Equipment</L></NavLink>}
              {hasReporting && <NavLink to="/reports" title={collapsed ? 'Reports' : undefined} className={navItem}><BarChart3 size={18} /> <L collapsed={collapsed}>Reports</L></NavLink>}
              {/* Company Admins only, and only when the plan carries it — the page and API check again. */}
              {user?.role === 'CUSTOMER_ADMIN' && hasAutomation && <NavLink to="/automation" title={collapsed ? 'Automation' : undefined} className={navItem}><Zap size={18} /> <L collapsed={collapsed}>Automation</L></NavLink>}
              <NavLink to="/settings" title={collapsed ? 'Settings' : undefined} className={navItem}><Settings size={18} /> <L collapsed={collapsed}>Settings</L></NavLink>
            </div>
            {!collapsed && user?.role === 'CUSTOMER_ADMIN' && seats && (
              <div className="mt-6 rounded-xl border border-brand-200 bg-brand-50 p-4">
                <div className="flex justify-between text-sm font-semibold text-brand-700"><span>Team Seats</span><span>{seatsUsed}/{seatsMax}</span></div>
                <ProgressBar value={seatsUsed} max={seatsMax} neutral className="mt-2" />
                <p className="mt-2 text-sm text-brand-600">{Math.max(0, seatsMax - seatsUsed)} seats remaining</p>
              </div>
            )}
          </>
        )}
      </nav>

      <div className="border-t border-line p-3">
        {!collapsed && portal === 'client' && myCompany.data && (
          <div className="mb-2 rounded-lg bg-gray-50 px-3 py-2.5">
            <p className="flex items-center gap-2 text-sm font-semibold"><Building2 size={14} className="text-brand-600" /> {myCompany.data.name}</p>
            <p className="text-xs text-muted">{seats ? (seats.subscriptionType === 'CUSTOM' ? 'Custom Plan' : `${seats.planName} Plan`) : ''}</p>
          </div>
        )}
        {collapsed ? (
          // Two separate controls stacked, because at 72px the row layout has nowhere
          // to put a name and a sign-out button side by side.
          <div className="flex flex-col items-center gap-1">
            <Link
              to={portal === 'client' ? '/settings' : '/profile'}
              title={`${user?.firstName ?? ''} ${user?.lastName ?? ''} — ${roleLabel(user?.role)}`}
              className="rounded-lg p-1 hover:bg-gray-50"
            >
              <Avatar firstName={user?.firstName} lastName={user?.lastName} src={api.mediaUrl(user?.photoUrl)} />
            </Link>
            <button onClick={() => void logout()} title="Sign out" aria-label="Sign out"
              className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink"><LogOut size={16} /></button>
          </div>
        ) : (
          <Link to={portal === 'client' ? '/settings' : '/profile'} className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-gray-50">
            <Avatar firstName={user?.firstName} lastName={user?.lastName} src={api.mediaUrl(user?.photoUrl)} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{user?.firstName} {user?.lastName}</p>
              <p className={cn('truncate text-xs', portal === 'admin' ? 'text-brand-600' : 'text-muted')}>{roleLabel(user?.role)}</p>
            </div>
            <button onClick={(e) => { e.preventDefault(); void logout() }} className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" aria-label="Sign out"><LogOut size={16} /></button>
          </Link>
        )}
      </div>
    </aside>
  )
}

function roleLabel(role?: Role) {
  switch (role) {
    case 'ADMIN': return 'Service Provider'
    case 'ENGINEER': return 'Engineer'
    case 'STAFF': return 'Employee'
    case 'CUSTOMER_ADMIN': return 'Company Admin'
    case 'CUSTOMER': return 'Member'
    default: return ''
  }
}

/** The admin pages a custom role can open, for the engineer and Default (staff) portals. */
function PrivilegedLinks({ collapsed, navItem, hasPrivilege }: {
  collapsed: boolean
  navItem: ReturnType<typeof makeNavItem>
  hasPrivilege: (p: Privilege) => boolean
}) {
  const { user } = useAuth()
  // An Engineer already has Knowledge Base in their main list; a Default employee gets it here.
  const kbHere = hasPrivilege('MANAGE_KNOWLEDGE_BASE') && user?.role !== 'ENGINEER'
  const any = kbHere || (['MANAGE_CONTRACTS', 'MANAGE_USERS', 'MANAGE_PLANS', 'MANAGE_ROLES', 'MANAGE_TEMPLATES', 'VIEW_AUDIT_LOG', 'MANAGE_ASSETS'] as Privilege[])
    .some((p) => hasPrivilege(p))
  if (!any) return null
  return (
    <div className="mt-6">
      {!collapsed && <div className="px-3 pb-2"><SectionTitle>Administration</SectionTitle></div>}
      <div className="space-y-1">
        {hasPrivilege('MANAGE_USERS') && <NavLink to="/admin/users" title={collapsed ? 'Users' : undefined} className={navItem}><Users size={18} /> <L collapsed={collapsed}>Users</L></NavLink>}
        {hasPrivilege('MANAGE_CONTRACTS') && <NavLink to="/admin/contracts" title={collapsed ? 'Service Contracts' : undefined} className={navItem}><FileSignature size={18} /> <L collapsed={collapsed}>Service Contracts</L></NavLink>}
        {hasPrivilege('MANAGE_ASSETS') && <NavLink to="/admin/assets" title={collapsed ? 'Assets' : undefined} className={navItem}><HardDrive size={18} /> <L collapsed={collapsed}>Assets</L></NavLink>}
        {hasPrivilege('MANAGE_PLANS') && <NavLink to="/admin/plans" title={collapsed ? 'Subscription Plans' : undefined} className={navItem}><Layers size={18} /> <L collapsed={collapsed}>Subscription Plans</L></NavLink>}
        {hasPrivilege('MANAGE_TEMPLATES') && <NavLink to="/admin/templates" title={collapsed ? 'Templates' : undefined} className={navItem}><FileText size={18} /> <L collapsed={collapsed}>Templates</L></NavLink>}
        {kbHere && <KbNavLink collapsed={collapsed} navItem={navItem} />}
        {hasPrivilege('MANAGE_ROLES') && <NavLink to="/admin/roles" title={collapsed ? 'Role Management' : undefined} className={navItem}><KeyRound size={18} /> <L collapsed={collapsed}>Role Management</L></NavLink>}
        {hasPrivilege('VIEW_AUDIT_LOG') && <NavLink to="/admin/audit" title={collapsed ? 'Audit Log' : undefined} className={navItem}><ScrollText size={18} /> <L collapsed={collapsed}>Audit Log</L></NavLink>}
      </div>
    </div>
  )
}

/**
 * Knowledge Base. Stays highlighted on the reader pages (/kb) and the writer pages (/admin/kb).
 * For Admins and Knowledge Base managers it carries the "Needs publishing" count — articles an
 * engineer has asked them to publish (collapsed: a dot on the icon).
 */
function KbNavLink({ collapsed, navItem }: { collapsed: boolean; navItem: ReturnType<typeof makeNavItem> }) {
  const { pathname } = useLocation()
  const { isManager } = useKbAccess()
  const waiting = useKbRequestedCount(isManager)
  const active = pathname === '/kb' || pathname.startsWith('/kb/') || pathname.startsWith('/admin/kb')
  const hint = waiting > 0 ? `${waiting} article${waiting === 1 ? '' : 's'} waiting to be published` : undefined
  return (
    <NavLink to="/kb" title={collapsed ? ['Knowledge Base', hint].filter(Boolean).join(' — ') : hint}
      className={() => cn(navItem({ isActive: active }), 'relative')}>
      <BookOpen size={18} /> <L collapsed={collapsed}>Knowledge Base</L>
      {waiting > 0 && (collapsed
        ? <span className="absolute right-3 top-2 h-2 w-2 rounded-full bg-purple-500 ring-2 ring-surface" aria-hidden />
        : <span className="ml-auto rounded-full bg-purple-100 px-2 py-0.5 text-xs font-semibold text-purple-700" aria-label={hint}>{waiting}</span>)}
    </NavLink>
  )
}
