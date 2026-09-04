import { useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Crown, MailCheck, Pencil, Plus, Search, User as UserIcon, Wrench } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Checkbox, Field, Input } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { Avatar } from '../../components/ui/Avatar'
import { GenericBadge, UserStatusBadge } from '../../components/ui/Badge'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { usersApi } from '../../api/users'
import { companiesApi } from '../../api/companies'
import { api, errorMessage, isApiError } from '../../lib/api'
import { formatMonthYear } from '../../lib/format'
import { isValidEmail } from '../../lib/countries'
import type { CreateUserRequest, Role, User, UserStatus } from '../../types'

const ROLE_OPTS: { value: Role; label: string; description: string; icon: React.ReactNode }[] = [
  { value: 'ENGINEER', label: 'Engineer', description: 'Works tickets for assigned companies', icon: <Wrench size={15} className="text-blue-600" /> },
  { value: 'ADMIN', label: 'Admin', description: 'Engineer + full service-provider administration', icon: <Crown size={15} className="text-purple-600" /> },
  { value: 'CUSTOMER_ADMIN', label: 'Company Admin', description: 'Customer who manages their company team', icon: <Building2 size={15} className="text-brand-600" /> },
  { value: 'CUSTOMER', label: 'Customer', description: 'Raises and follows their company tickets', icon: <UserIcon size={15} className="text-gray-500" /> },
]
const roleLabel = (r: Role) => ROLE_OPTS.find((o) => o.value === r)?.label ?? r
type Filter = 'ALL' | 'STAFF' | 'CUSTOMERS' | Role

/** Admin Panel → Users: everyone in the system (staff and customer contacts). */
export default function UsersPage() {
  const [params] = useSearchParams()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [q, setQ] = useState('')
  const [inviteOpen, setInviteOpen] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)

  const users = useQuery({ queryKey: ['users', 'all'], queryFn: () => usersApi.list() })
  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list })
  const companyFilter = params.get('companyId') ? Number(params.get('companyId')) : undefined

  const rows = useMemo(() => (users.data ?? []).filter((u) => {
    if (companyFilter && u.companyId !== companyFilter) return false
    if (filter === 'STAFF' && !(u.role === 'ADMIN' || u.role === 'ENGINEER')) return false
    if (filter === 'CUSTOMERS' && !(u.role === 'CUSTOMER' || u.role === 'CUSTOMER_ADMIN')) return false
    if (filter !== 'ALL' && filter !== 'STAFF' && filter !== 'CUSTOMERS' && u.role !== filter) return false
    if (status && u.status !== status) return false
    const needle = q.trim().toLowerCase()
    return !needle || `${u.firstName} ${u.lastName} ${u.email} ${u.companyName ?? ''}`.toLowerCase().includes(needle)
  }), [users.data, companyFilter, filter, status, q])

  return (
    <>
      <Topbar title="User Management" subtitle="Engineers, admins and customer contacts" newTicket={false} actions={<Button onClick={() => setInviteOpen(true)}><Plus size={16} /> Add User</Button>} />
      <Page>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="relative min-w-64 flex-1">
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, email or company…" className="h-10 w-full rounded-lg border border-line bg-white pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
          </div>
          <SelectMenu<Filter> className="w-48" value={filter} onChange={setFilter} options={[
            { value: 'ALL', label: 'All users' }, { value: 'STAFF', label: 'Staff (engineers + admins)' }, { value: 'CUSTOMERS', label: 'Customers' },
            ...ROLE_OPTS.map((r) => ({ value: r.value, label: r.label, icon: r.icon })),
          ]} />
          <SelectMenu<UserStatus | ''> className="w-40" value={status} onChange={setStatus} options={[{ value: '', label: 'All statuses' }, { value: 'ACTIVE', label: 'Active' }, { value: 'INVITED', label: 'Invited' }, { value: 'INACTIVE', label: 'Inactive' }]} />
          <span className="whitespace-nowrap text-sm text-muted">{rows.length} users</span>
        </div>

        <Card className="overflow-hidden">
          <Table>
            <thead className="bg-gray-50/60"><tr><Th>User</Th><Th>Role</Th><Th>Company / Assigned</Th><Th>Status</Th><Th>Joined</Th><Th></Th></tr></thead>
            <tbody>
              {rows.map((u) => (
                <Tr key={u.id}>
                  <Td>
                    <div className="flex items-center gap-3">
                      <Avatar firstName={u.firstName} lastName={u.lastName} src={api.mediaUrl(u.photoUrl)} />
                      <div><p className="font-medium">{u.firstName} {u.lastName}</p><p className="text-xs text-muted">{u.email}</p></div>
                    </div>
                  </Td>
                  <Td>
                    <div className="flex flex-wrap gap-1.5">
                      {(u.role === 'ENGINEER' || u.role === 'ADMIN') && <GenericBadge tone="blue"><Wrench size={12} className="mr-1 inline" />Engineer</GenericBadge>}
                      {u.role === 'ADMIN' && <GenericBadge tone="purple"><Crown size={12} className="mr-1 inline" />Admin</GenericBadge>}
                      {u.role === 'CUSTOMER_ADMIN' && <GenericBadge tone="brand"><Crown size={12} className="mr-1 inline" />Company Admin</GenericBadge>}
                      {u.role === 'CUSTOMER' && <GenericBadge>Customer</GenericBadge>}
                      {u.additionalRoles?.map((r) => <GenericBadge key={r} tone="purple">+ {roleLabel(r)}</GenericBadge>)}
                    </div>
                  </Td>
                  <Td>
                    {u.companyName ? <span className="inline-flex items-center gap-1.5 text-sm"><Building2 size={14} className="text-muted" /> {u.companyName}</span>
                      : u.assignedCompanies && u.assignedCompanies.length > 0 ? <div className="flex flex-wrap gap-1.5">{u.assignedCompanies.map((c) => <GenericBadge key={c.id}>{c.name}</GenericBadge>)}</div>
                      : <span className="italic text-gray-400">None assigned</span>}
                  </Td>
                  <Td><UserStatusBadge status={u.status} /></Td>
                  <Td className="text-muted">{formatMonthYear(u.createdAt)}</Td>
                  <Td className="text-right"><button onClick={() => setEditing(u)} className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" aria-label="Edit"><Pencil size={15} /></button></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          {!users.isLoading && rows.length === 0 && <EmptyState title="No users match" hint="Adjust the filters, or add a user — they'll receive an activation email." action={<Button onClick={() => setInviteOpen(true)}><Plus size={16} /> Add User</Button>} />}
        </Card>
      </Page>
      <InviteUserModal open={inviteOpen} onClose={() => setInviteOpen(false)} companies={companies.data ?? []} defaultCompanyId={companyFilter} />
      {editing && <EditUserModal user={editing} companies={companies.data ?? []} onClose={() => setEditing(null)} />}
    </>
  )
}

function InviteUserModal({ open, onClose, companies, defaultCompanyId }: { open: boolean; onClose: () => void; companies: { id: number; name: string }[]; defaultCompanyId?: number }) {
  const qc = useQueryClient()
  const toast = useToast()
  const blank: CreateUserRequest = { firstName: '', lastName: '', email: '', phone: '', role: defaultCompanyId ? 'CUSTOMER' : 'ENGINEER', companyId: defaultCompanyId }
  const [form, setForm] = useState<CreateUserRequest>(blank)
  const [touched, setTouched] = useState(false)
  const needsCompany = form.role === 'CUSTOMER' || form.role === 'CUSTOMER_ADMIN'
  const emailOk = isValidEmail(form.email)
  const phoneOk = /^\+[0-9]{8,16}$/.test(form.phone)

  const create = useMutation({
    mutationFn: () => usersApi.create({ ...form, companyId: needsCompany ? form.companyId : undefined }),
    onSuccess: (u) => { void qc.invalidateQueries({ queryKey: ['users'] }); void qc.invalidateQueries({ queryKey: ['companies'] }); toast(`Invitation sent to ${u.email}`); onClose(); setForm(blank); setTouched(false) },
  })
  const fields = isApiError(create.error) ? create.error.fields ?? {} : {}
  const set = <K extends keyof CreateUserRequest>(k: K, v: CreateUserRequest[K]) => setForm((f) => ({ ...f, [k]: v }))
  const submit = (e: FormEvent) => { e.preventDefault(); setTouched(true); if (emailOk && phoneOk && (!needsCompany || form.companyId)) create.mutate() }

  return (
    <Modal open={open} onClose={onClose} title="Add user" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="invite-user" loading={create.isPending}>Send invitation</Button></>}>
      <form id="invite-user" onSubmit={submit} className="space-y-4" noValidate>
        {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
        <Alert tone="info">They'll get an email to confirm their address and choose their own password — you never see it.</Alert>
        <Field label="Role"><SelectMenu value={form.role} onChange={(r) => set('role', r)} options={ROLE_OPTS} /></Field>
        {needsCompany && (
          <Field label="Company" error={fields.companyId ?? (touched && !form.companyId ? 'Choose a company' : undefined)}>
            <SelectMenu<number> value={form.companyId ?? ''} placeholder="Select company…" onChange={(v) => set('companyId', v)} options={companies.map((c) => ({ value: c.id, label: c.name }))} invalid={touched && !form.companyId} />
          </Field>
        )}
        <div className="grid grid-cols-2 gap-4">
          <Field label="First name" error={fields.firstName}><Input required value={form.firstName} onChange={(e) => set('firstName', e.target.value)} /></Field>
          <Field label="Last name" error={fields.lastName}><Input required value={form.lastName} onChange={(e) => set('lastName', e.target.value)} /></Field>
        </div>
        <Field label="Email" error={fields.email ?? (touched && !emailOk ? 'Enter a valid email address' : undefined)}>
          <Input type="email" required value={form.email} invalid={touched && !emailOk} onBlur={() => setTouched(true)} onChange={(e) => set('email', e.target.value)} placeholder="name@company.com" />
        </Field>
        <Field label="Phone" error={fields.phone ?? (touched && !phoneOk ? 'Enter a valid phone number' : undefined)}>
          <PhoneInput value={form.phone} onChange={(v) => set('phone', v)} invalid={touched && !phoneOk} />
        </Field>
      </form>
    </Modal>
  )
}

function EditUserModal({ user, companies, onClose }: { user: User; companies: { id: number; name: string }[]; onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const isStaff = user.role === 'ENGINEER' || user.role === 'ADMIN'
  const [selected, setSelected] = useState<Set<number>>(new Set(user.assignedCompanies?.map((c) => c.id) ?? []))
  const [roles, setRoles] = useState<Set<Role>>(new Set(user.additionalRoles ?? []))
  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['users'] }); void qc.invalidateQueries({ queryKey: ['companies'] }) }

  const save = useMutation({ mutationFn: () => usersApi.setCompanies(user.id, [...selected]), onSuccess: () => { invalidate(); toast('Assignments saved'); onClose() } })
  const saveRoles = useMutation({ mutationFn: () => usersApi.setRoles(user.id, [...roles]), onSuccess: () => { invalidate(); toast('Roles updated'); onClose() }, onError: (e) => toast(errorMessage(e), 'error') })
  const resend = useMutation({ mutationFn: () => usersApi.resendInvite(user.id), onSuccess: () => toast('Invitation re-sent'), onError: (e) => toast(errorMessage(e), 'error') })
  const setStatus = useMutation({ mutationFn: (s: UserStatus) => usersApi.setStatus(user.id, s), onSuccess: () => { invalidate(); toast('Status updated'); onClose() }, onError: (e) => toast(errorMessage(e), 'error') })
  const remove = useMutation({ mutationFn: () => usersApi.remove(user.id), onSuccess: () => { invalidate(); toast('User removed'); onClose() }, onError: (e) => toast(errorMessage(e), 'error') })
  const toggle = (id: number, on: boolean) => setSelected((s) => { const n = new Set(s); if (on) n.add(id); else n.delete(id); return n })
  const toggleRole = (r: Role, on: boolean) => setRoles((s) => { const n = new Set(s); if (on) n.add(r); else n.delete(r); return n })

  return (
    <Modal open onClose={onClose} title={`${user.firstName} ${user.lastName}`}
      footer={<><Button variant="secondary" onClick={onClose}>Close</Button>{isStaff && <Button onClick={() => save.mutate()} loading={save.isPending}>Save assignments</Button>}</>}>
      {save.isError && <Alert className="mb-4">{errorMessage(save.error)}</Alert>}
      <div className="mb-5 flex items-center gap-3">
        <Avatar size="lg" firstName={user.firstName} lastName={user.lastName} src={api.mediaUrl(user.photoUrl)} />
        <div>
          <p className="font-medium">{user.email}</p>
          <p className="flex items-center gap-2 text-sm text-muted">{user.userCode} · {roleLabel(user.role)} <UserStatusBadge status={user.status} /></p>
          {user.companyName && <p className="text-sm text-muted">{user.companyName}</p>}
        </div>
      </div>
      {isStaff && (
        <>
          <p className="mb-2 text-sm font-medium">Assigned companies</p>
          <div className="grid grid-cols-2 gap-2 rounded-lg border border-line p-3">
            {companies.map((c) => <Checkbox key={c.id} label={c.name} checked={selected.has(c.id)} onChange={(e) => toggle(c.id, e.target.checked)} />)}
            {companies.length === 0 && <p className="col-span-2 text-sm text-muted">No companies yet.</p>}
          </div>
        </>
      )}
      <div className="mt-4">
        <p className="mb-2 text-sm font-medium">Additional roles</p>
        <p className="mb-2 text-xs text-muted">Grant extra privileges on top of their primary role — e.g. give an Engineer Admin access too.</p>
        {saveRoles.isError && <Alert className="mb-2">{errorMessage(saveRoles.error)}</Alert>}
        <div className="grid grid-cols-2 gap-2 rounded-lg border border-line p-3">
          {ROLE_OPTS.filter((o) => o.value !== user.role).map((o) => (
            <Checkbox key={o.value} label={o.label} checked={roles.has(o.value)} onChange={(e) => toggleRole(o.value, e.target.checked)} />
          ))}
        </div>
        <div className="mt-2 flex justify-end">
          <Button size="sm" variant="secondary" onClick={() => saveRoles.mutate()} loading={saveRoles.isPending}>Save roles</Button>
        </div>
      </div>
      <div className="mt-5 flex flex-wrap gap-2 border-t border-line pt-4">
        {user.status === 'INVITED' && <Button size="sm" variant="secondary" onClick={() => resend.mutate()} loading={resend.isPending}><MailCheck size={14} /> Resend invitation</Button>}
        {user.status === 'ACTIVE' && <Button size="sm" variant="secondary" onClick={() => setStatus.mutate('INACTIVE')}>Deactivate</Button>}
        {user.status === 'INACTIVE' && <Button size="sm" variant="secondary" onClick={() => setStatus.mutate('ACTIVE')}>Reactivate</Button>}
        <Button size="sm" variant="danger" onClick={() => confirm(`Remove ${user.firstName} ${user.lastName}?`) && remove.mutate()}>Remove</Button>
      </div>
    </Modal>
  )
}
