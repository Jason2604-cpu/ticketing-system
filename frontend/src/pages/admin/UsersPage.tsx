import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, MailCheck, Pencil, Plus, Search, Users as UsersIcon } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Checkbox, Field, Input } from '../../components/ui/Input'
import { SelectMenu, type SelectOption } from '../../components/ui/SelectMenu'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { Avatar } from '../../components/ui/Avatar'
import { GenericBadge, UserStatusBadge } from '../../components/ui/Badge'
import { UserRoleBadges } from '../../components/ui/RoleBadge'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { useAuth } from '../../auth/useAuth'
import { usersApi, teamApi } from '../../api/users'
import { companiesApi } from '../../api/companies'
import { rolesApi } from '../../api/roles'
import { api, errorMessage, isApiError } from '../../lib/api'
import { formatMonthYear } from '../../lib/format'
import { isValidEmail } from '../../lib/countries'
import { CUSTOM_ROLE_ICON, ROLE_META, ROLE_ORDER, isCustomerRole, isStaffRole, roleLabel } from '../../lib/roleMeta'
import type { CreateUserRequest, Role, User, UserStatus } from '../../types'

/** Role picker options, from the one shared role definition (same icons as the badges). */
const roleOption = (r: Role): SelectOption<Role> => {
  const m = ROLE_META[r]
  const Icon = m.icon
  return { value: r, label: m.label, description: m.description, icon: <Icon size={15} className="text-muted" /> }
}
const ROLE_OPTS = ROLE_ORDER.map(roleOption)

/**
 * The list filter. Groups first, then one entry per role — the same roles the badges show, so
 * "Admin" lists exactly the rows with an Admin badge — then one entry per custom role in use.
 */
type Filter = 'ALL' | 'EMPLOYEES' | 'CUSTOMERS' | Role | `custom:${number}`

const sameSet = <T,>(a: Set<T>, b: Set<T>) => a.size === b.size && [...a].every((x) => b.has(x))

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

  // Custom roles actually held by someone, for the filter (no extra request: it's in the user rows).
  const customRolesInUse = useMemo(() => {
    const byId = new Map<number, string>()
    for (const u of users.data ?? []) for (const r of u.customRoles ?? []) byId.set(r.id, r.name)
    return [...byId.entries()].sort((a, b) => a[1].localeCompare(b[1]))
  }, [users.data])

  const filterOptions: SelectOption<Filter>[] = [
    { value: 'ALL', label: 'All users' },
    { value: 'EMPLOYEES', label: 'All employees', icon: <UsersIcon size={15} className="text-muted" /> },
    { value: 'CUSTOMERS', label: 'All customers', icon: <Building2 size={15} className="text-muted" /> },
    ...ROLE_OPTS.map((o) => ({ ...o, description: undefined })),
    ...customRolesInUse.map(([id, name]) => ({ value: `custom:${id}` as Filter, label: name, icon: <CUSTOM_ROLE_ICON size={15} className="text-muted" /> })),
  ]

  const rows = useMemo(() => (users.data ?? []).filter((u) => {
    if (companyFilter && u.companyId !== companyFilter) return false
    if (filter === 'EMPLOYEES' && !isStaffRole(u.role)) return false
    if (filter === 'CUSTOMERS' && !isCustomerRole(u.role)) return false
    if (typeof filter === 'string' && filter.startsWith('custom:')) {
      const id = Number(filter.slice('custom:'.length))
      if (!u.customRoles?.some((r) => r.id === id)) return false
    } else if (filter !== 'ALL' && filter !== 'EMPLOYEES' && filter !== 'CUSTOMERS' && u.role !== filter) {
      return false
    }
    if (status && u.status !== status) return false
    const needle = q.trim().toLowerCase()
    return !needle || `${u.firstName} ${u.lastName} ${u.email} ${u.companyName ?? ''}`.toLowerCase().includes(needle)
  }), [users.data, companyFilter, filter, status, q])

  return (
    <>
      <Topbar title="User Management" subtitle="Employees and customer contacts" newTicket={false} actions={<Button onClick={() => setInviteOpen(true)}><Plus size={16} /> Add User</Button>} />
      <Page>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="relative min-w-64 flex-1">
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, email or company…" className="h-10 w-full rounded-lg border border-line bg-surface pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
          </div>
          <SelectMenu<Filter> className="w-52" value={filter} onChange={setFilter} options={filterOptions} />
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
                  <Td><UserRoleBadges role={u.role} customRoles={u.customRoles} /></Td>
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
  // New employees start on Default (no modules) — tick a role for them once they're in.
  const blank: CreateUserRequest = { firstName: '', lastName: '', email: '', phone: '', role: defaultCompanyId ? 'CUSTOMER' : 'STAFF', companyId: defaultCompanyId }
  const [form, setForm] = useState<CreateUserRequest>(blank)
  const [touched, setTouched] = useState(false)
  const needsCompany = isCustomerRole(form.role)
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
  const { user: me, hasRole } = useAuth()
  const isEmployee = isStaffRole(user.role)
  const isCustomerSide = isCustomerRole(user.role)
  // The API lets only an Admin change roles, and nobody their own.
  const isSelf = me?.id === user.id
  const canEditRoles = hasRole('ADMIN') && !isSelf

  // ── Form state — everything below is saved by the one Save button ──────────
  const originalCustom = useMemo(() => new Set(user.customRoles?.map((r) => r.id) ?? []), [user.customRoles])
  const originalCompanies = useMemo(() => new Set(user.assignedCompanies?.map((c) => c.id) ?? []), [user.assignedCompanies])
  const [admin, setAdmin] = useState(user.role === 'ADMIN')
  const [engineer, setEngineer] = useState(user.role === 'ENGINEER')
  const [customRoleIds, setCustomRoleIds] = useState<Set<number>>(new Set(originalCustom))
  const [selected, setSelected] = useState<Set<number>>(new Set(originalCompanies))
  const [companyAdmin, setCompanyAdmin] = useState(user.role === 'CUSTOMER_ADMIN')

  const customRoles = useQuery({ queryKey: ['roles'], queryFn: rolesApi.list, enabled: isEmployee && canEditRoles })
  const grantable = (customRoles.data ?? []).filter((r) => r.custom)

  // Admin includes Engineer and every privilege, so ticking it covers the rest.
  const nextRole: Role = isEmployee ? (admin ? 'ADMIN' : engineer ? 'ENGINEER' : 'STAFF') : companyAdmin ? 'CUSTOMER_ADMIN' : 'CUSTOMER'
  const nextCustom = admin ? new Set<number>() : customRoleIds
  const worksTickets = nextRole === 'ADMIN' || nextRole === 'ENGINEER'
  const rolesChanged = isEmployee && (nextRole !== user.role || !sameSet(nextCustom, originalCustom))
  const companiesChanged = worksTickets && !sameSet(selected, originalCompanies)
  const companyAdminChanged = isCustomerSide && nextRole !== user.role
  const dirty = rolesChanged || companiesChanged || companyAdminChanged

  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['users'] }); void qc.invalidateQueries({ queryKey: ['companies'] }); void qc.invalidateQueries({ queryKey: ['team'] }) }

  const save = useMutation({
    mutationFn: async () => {
      // Roles first: moving someone to Engineer/Admin is what allows company assignments.
      if (rolesChanged && canEditRoles) {
        await usersApi.setRoles(user.id, admin ? ['ADMIN'] : engineer ? ['ENGINEER'] : [], [...nextCustom])
      }
      if (companiesChanged) await usersApi.setCompanies(user.id, [...selected])
      if (companyAdminChanged) await teamApi.setAdmin(user.companyId!, user.id, companyAdmin)
    },
    onSuccess: () => { invalidate(); toast('Changes saved'); onClose() },
    // Part of it may have been saved before the error — refresh the list either way.
    onError: () => invalidate(),
  })
  const resend = useMutation({ mutationFn: () => usersApi.resendInvite(user.id), onSuccess: () => toast('Invitation re-sent'), onError: (e) => toast(errorMessage(e), 'error') })
  const setStatus = useMutation({ mutationFn: (s: UserStatus) => usersApi.setStatus(user.id, s), onSuccess: () => { invalidate(); toast('Status updated'); onClose() }, onError: (e) => toast(errorMessage(e), 'error') })
  const remove = useMutation({ mutationFn: () => usersApi.remove(user.id), onSuccess: () => { invalidate(); toast('User removed'); onClose() }, onError: (e) => toast(errorMessage(e), 'error') })
  const toggle = (id: number, on: boolean) => setSelected((s) => { const n = new Set(s); if (on) n.add(id); else n.delete(id); return n })
  const toggleCustomRole = (id: number, on: boolean) => setCustomRoleIds((s) => { const n = new Set(s); if (on) n.add(id); else n.delete(id); return n })

  return (
    <Modal open onClose={onClose} title={`${user.firstName} ${user.lastName}`}
      footer={<><Button variant="secondary" onClick={onClose}>Close</Button><Button onClick={() => save.mutate()} loading={save.isPending} disabled={!dirty}>Save</Button></>}>
      {save.isError && <Alert className="mb-4">{errorMessage(save.error)}</Alert>}
      <div className="mb-5 flex items-center gap-3">
        <Avatar size="lg" firstName={user.firstName} lastName={user.lastName} src={api.mediaUrl(user.photoUrl)} />
        <div>
          <p className="font-medium">{user.email}</p>
          <p className="flex items-center gap-2 text-sm text-muted">{user.userCode} · {roleLabel(user.role)} <UserStatusBadge status={user.status} /></p>
          {user.companyName && <p className="text-sm text-muted">{user.companyName}</p>}
        </div>
      </div>

      {isEmployee && (
        <div>
          <p className="mb-1 text-sm font-medium">Roles</p>
          {canEditRoles ? (
            <>
              <p className="mb-2 text-xs text-muted">Tick what this employee does. Nothing ticked = <span className="font-medium">Default</span>: they can sign in, but no module is open to them.</p>
              <div className="divide-y divide-line rounded-lg border border-line">
                <RoleRow icon={<RoleIcon role="ADMIN" />} label="Admin" description={ROLE_META.ADMIN.description}
                  checked={admin} onChange={setAdmin} />
                <RoleRow icon={<RoleIcon role="ENGINEER" />} label="Engineer" description={ROLE_META.ENGINEER.description}
                  checked={admin || engineer} disabled={admin} note={admin ? 'Included in Admin' : undefined} onChange={setEngineer} />
                {grantable.map((r) => (
                  <RoleRow key={r.id} icon={<CUSTOM_ROLE_ICON size={15} className="text-amber-700" />} label={r.name}
                    description={r.description || `${r.privileges.length} privilege${r.privileges.length === 1 ? '' : 's'}`}
                    checked={admin || customRoleIds.has(r.id)} disabled={admin} note={admin ? 'Included in Admin' : undefined}
                    onChange={(v) => toggleCustomRole(r.id, v)} />
                ))}
              </div>
              <p className="mt-2 flex items-center gap-2 text-xs text-muted">Saved as: <UserRoleBadges role={nextRole} customRoles={grantable.filter((r) => nextCustom.has(r.id)).map((r) => ({ id: r.id, name: r.name, privileges: r.privileges }))} /></p>
            </>
          ) : (
            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-line p-3">
              <UserRoleBadges role={user.role} customRoles={user.customRoles} />
              <span className="text-xs text-muted">{isSelf ? "You can't change your own roles — ask another admin." : 'Only an Admin can change roles.'}</span>
            </div>
          )}
        </div>
      )}

      {isEmployee && worksTickets && (
        <div className="mt-4">
          <p className="mb-1 text-sm font-medium">Assigned companies</p>
          <p className="mb-2 text-xs text-muted">{nextRole === 'ADMIN'
            ? 'An Admin sees every company anyway; this only lists them as an engineer on these.'
            : 'An Engineer only sees tickets of the companies ticked here (none ticked = all companies).'}</p>
          <div className="grid grid-cols-2 gap-2 rounded-lg border border-line p-3">
            {companies.map((c) => <Checkbox key={c.id} label={c.name} checked={selected.has(c.id)} onChange={(e) => toggle(c.id, e.target.checked)} />)}
            {companies.length === 0 && <p className="col-span-2 text-sm text-muted">No companies yet.</p>}
          </div>
        </div>
      )}

      {isCustomerSide && user.companyId && (
        <div className="flex items-center justify-between gap-4 rounded-lg border border-line p-3">
          <div>
            <p className="flex items-center gap-2 text-sm font-medium"><RoleIcon role="CUSTOMER_ADMIN" /> Company Admin</p>
            <p className="text-xs text-muted">Manages their company's team. Company Admin and Customer are mutually exclusive — this changes their role, it doesn't add a second one. Stays in sync with the Team Members page in their portal.</p>
          </div>
          <ToggleSwitch checked={companyAdmin} onChange={setCompanyAdmin} />
        </div>
      )}

      <div className="mt-5 flex flex-wrap gap-2 border-t border-line pt-4">
        {user.status === 'INVITED' && <Button size="sm" variant="secondary" onClick={() => resend.mutate()} loading={resend.isPending}><MailCheck size={14} /> Resend invitation</Button>}
        {user.status === 'ACTIVE' && <Button size="sm" variant="secondary" onClick={() => setStatus.mutate('INACTIVE')}>Deactivate</Button>}
        {user.status === 'INACTIVE' && <Button size="sm" variant="secondary" onClick={() => setStatus.mutate('ACTIVE')}>Reactivate</Button>}
        <Button size="sm" variant="danger" onClick={() => confirm(`Remove ${user.firstName} ${user.lastName}?`) && remove.mutate()}>Remove</Button>
      </div>
    </Modal>
  )
}

function RoleIcon({ role }: { role: Role }) {
  const Icon = ROLE_META[role].icon
  const color = { purple: 'text-purple-700', blue: 'text-blue-600', brand: 'text-brand-600', gray: 'text-gray-500', green: 'text-green-600', red: 'text-red-600', amber: 'text-amber-700' }[ROLE_META[role].tone]
  return <Icon size={15} className={color} />
}

/** One tickable role: icon, name, what it allows, and why it's locked when it is. */
function RoleRow({ icon, label, description, checked, disabled, note, onChange }: {
  icon: ReactNode; label: string; description: string; checked: boolean; disabled?: boolean; note?: string; onChange: (v: boolean) => void
}) {
  return (
    <label className={`flex items-start gap-3 px-3 py-2.5 ${disabled ? 'cursor-not-allowed' : 'cursor-pointer hover:bg-gray-50'}`}>
      <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-line text-brand-600 focus:ring-brand-500" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span className="mt-0.5">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2 text-sm font-medium">{label}{note && <GenericBadge>{note}</GenericBadge>}</span>
        <span className="block text-xs text-muted">{description}</span>
      </span>
    </label>
  )
}

function ToggleSwitch({ checked, disabled, onChange }: { checked: boolean; disabled?: boolean; onChange: (v: boolean) => void }) {
  return (
    <button type="button" role="switch" aria-checked={checked} disabled={disabled} onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:opacity-50 ${checked ? 'bg-brand-600' : 'bg-gray-200'}`}>
      <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-surface shadow transition-transform ${checked ? 'left-0.5 translate-x-5' : 'left-0.5'}`} />
    </button>
  )
}
