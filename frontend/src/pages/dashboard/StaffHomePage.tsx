import { Link } from 'react-router-dom'
import { BookOpen, Building2, FileSignature, FileText, HardDrive, KeyRound, Layers, Lock, ScrollText, Users, type LucideIcon } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/Misc'
import { useAuth } from '../../auth/useAuth'
import type { Privilege } from '../../types'

const MODULES: { privilege: Privilege; to: string; label: string; hint: string; icon: LucideIcon }[] = [
  { privilege: 'MANAGE_USERS', to: '/admin/users', label: 'Users', hint: 'Invite and manage users', icon: Users },
  { privilege: 'MANAGE_COMPANIES', to: '/companies', label: 'Companies', hint: 'Customer companies and subscriptions', icon: Building2 },
  { privilege: 'MANAGE_CONTRACTS', to: '/admin/contracts', label: 'Service Contracts', hint: 'Recurring visits and renewals', icon: FileSignature },
  { privilege: 'MANAGE_ASSETS', to: '/admin/assets', label: 'Assets', hint: 'Customer equipment and warranties', icon: HardDrive },
  { privilege: 'MANAGE_PLANS', to: '/admin/plans', label: 'Subscription Plans', hint: 'Plans, features and limits', icon: Layers },
  { privilege: 'MANAGE_TEMPLATES', to: '/admin/templates', label: 'Templates', hint: 'Ticket templates and canned replies', icon: FileText },
  { privilege: 'MANAGE_KNOWLEDGE_BASE', to: '/admin/kb', label: 'Knowledge Base', hint: 'Write, review and publish articles', icon: BookOpen },
  { privilege: 'MANAGE_ROLES', to: '/admin/roles', label: 'Role Management', hint: 'Custom roles and their privileges', icon: KeyRound },
  { privilege: 'VIEW_AUDIT_LOG', to: '/admin/audit', label: 'Audit Log', hint: 'Sign-ins and changes', icon: ScrollText },
]

/**
 * Home for an employee with the Default role. Default on its own opens no module; custom roles
 * (granted on the Users page) each open specific pages, which are listed here as shortcuts.
 */
export default function StaffHomePage() {
  const { user, hasPrivilege } = useAuth()
  const modules = MODULES.filter((m) => hasPrivilege(m.privilege))

  return (
    <>
      <Topbar title={`Welcome, ${user?.firstName ?? ''}`} />
      <Page>
        {modules.length === 0 ? (
          <Card>
            <CardBody>
              <EmptyState
                icon={<Lock size={22} />}
                title="No modules assigned yet"
                hint="Your account has the Default role, which can sign in but doesn't open any module. An administrator can give you access by ticking a role for you in User Management."
              />
            </CardBody>
          </Card>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {modules.map((m) => {
              const Icon = m.icon
              return (
                <Link key={m.to} to={m.to} className="block">
                  <Card className="transition-shadow hover:shadow-md">
                    <CardBody className="flex items-center gap-4 pt-5">
                      <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600"><Icon size={20} /></span>
                      <div>
                        <p className="font-semibold">{m.label}</p>
                        <p className="text-sm text-muted">{m.hint}</p>
                      </div>
                    </CardBody>
                  </Card>
                </Link>
              )
            })}
          </div>
        )}
      </Page>
    </>
  )
}
