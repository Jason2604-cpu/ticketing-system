import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Building2, Pencil, Plus } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Avatar, CompanyMark } from '../../components/ui/Avatar'
import { GenericBadge } from '../../components/ui/Badge'
import { EmptyState, ProgressBar } from '../../components/ui/Misc'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { companiesApi } from '../../api/companies'
import { api } from '../../lib/api'
import { useAuth } from '../../auth/useAuth'

/** Admin Panel → Companies: plan, seat usage, assigned engineers, company admin. */
export default function CompaniesPage() {
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const isAdmin = hasRole('ADMIN')
  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list })

  const planTone = (name?: string) => (name?.toLowerCase().includes('enterprise') ? 'purple' : name?.toLowerCase().includes('starter') ? 'gray' : 'blue') as 'purple' | 'gray' | 'blue'

  return (
    <>
      <Topbar title="Company Management" subtitle="Konoha HelpDesk · Service Provider Admin" newTicket={!isAdmin}
        actions={isAdmin && <Link to="/companies/new"><Button><Plus size={16} /> Add Company</Button></Link>} />
      <Page>
        <Card className="overflow-hidden">
          <Table>
            <thead className="bg-gray-50/60"><tr><Th>Company</Th><Th>Plan</Th><Th>Client Limit</Th><Th>Assigned Engineers</Th><Th>Company Admin</Th><Th></Th></tr></thead>
            <tbody>
              {companies.data?.map((c) => (
                <Tr key={c.id} onClick={() => navigate(`/companies/${c.id}`)}>
                  <Td>
                    <div className="flex items-center gap-3">
                      <CompanyMark name={c.name} src={api.mediaUrl(c.logoUrl)} />
                      <div><p className="font-medium">{c.name}</p><p className="text-xs text-muted">{c.contactEmail ?? '—'}</p></div>
                    </div>
                  </Td>
                  <Td>{c.planName ? <GenericBadge tone={planTone(c.planName)}>{c.planName}</GenericBadge> : <span className="text-gray-400">—</span>}</Td>
                  <Td>
                    {c.maxUsers != null ? (
                      <div className="flex items-center gap-3">
                        <span className="whitespace-nowrap text-sm"><b>{c.activeUsers ?? 0}</b> <span className="text-muted">/ {c.maxUsers}</span></span>
                        <ProgressBar value={c.activeUsers ?? 0} max={c.maxUsers} neutral className="w-20" />
                      </div>
                    ) : <span className="text-gray-400">—</span>}
                  </Td>
                  <Td>
                    {c.engineers && c.engineers.length > 0 ? (
                      <div className="flex -space-x-1">
                        {c.engineers.slice(0, 4).map((e) => <Avatar key={e.id} size="sm" firstName={e.firstName} lastName={e.lastName} src={api.mediaUrl(e.photoUrl)} className="ring-2 ring-surface" />)}
                        {c.engineers.length > 4 && <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gray-100 text-[11px] font-semibold ring-2 ring-surface">+{c.engineers.length - 4}</span>}
                      </div>
                    ) : <span className="italic text-gray-400">Unassigned</span>}
                  </Td>
                  <Td>
                    {c.companyAdmin ? <div><p className="font-medium">{c.companyAdmin.firstName} {c.companyAdmin.lastName}</p><p className="text-xs text-muted">{c.companyAdmin.email}</p></div> : <span className="italic text-gray-400">No admin yet</span>}
                  </Td>
                  <Td className="text-right">
                    <Link to={`/companies/${c.id}`} onClick={(e) => e.stopPropagation()} className="inline-block rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" aria-label="Open"><Pencil size={15} /></Link>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          {companies.data?.length === 0 && (
            <EmptyState icon={<Building2 size={22} />} title="No companies yet" hint="Create the first customer company and pick its subscription plan."
              action={isAdmin && <Link to="/companies/new"><Button><Plus size={16} /> Add Company</Button></Link>} />
          )}
        </Card>
      </Page>
    </>
  )
}
