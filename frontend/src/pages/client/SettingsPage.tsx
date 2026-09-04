import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Building2, Crown, Lock, MailCheck, Plus, Save, Shield, User as UserIcon, UserCheck, Users, UserX } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Checkbox, Field, Input } from '../../components/ui/Input'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { isValidEmail } from '../../lib/countries'
import { Avatar } from '../../components/ui/Avatar'
import { GenericBadge, UserStatusBadge } from '../../components/ui/Badge'
import { Alert, ProgressBar } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { useAuth } from '../../auth/useAuth'
import { authApi } from '../../api/auth'
import { profileApi, teamApi } from '../../api/users'
import { companiesApi } from '../../api/companies'
import { api, errorMessage, isApiError } from '../../lib/api'
import { cn } from '../../lib/cn'
import { formatMonthYear } from '../../lib/format'
import type { InviteTeamMemberRequest, NotificationPreferences, User, UserStatus } from '../../types'

type Tab = 'profile' | 'team' | 'company' | 'notifications' | 'security'

/** Client Portal → Settings. Also used as the profile page for staff (fewer tabs). */
export default function SettingsPage() {
  const { user, hasRole } = useAuth()
  const isCustomerAdmin = hasRole('CUSTOMER_ADMIN')
  const isClient = hasRole('CUSTOMER', 'CUSTOMER_ADMIN')
  const [tab, setTab] = useState<Tab>('profile')

  const tabs: { key: Tab; label: string; icon: ReactNode; show: boolean }[] = [
    { key: 'profile', label: 'Profile', icon: <UserIcon size={16} />, show: true },
    { key: 'team', label: 'Team Members', icon: <Users size={16} />, show: isCustomerAdmin },
    { key: 'company', label: 'Company Info', icon: <Building2 size={16} />, show: isClient },
    { key: 'notifications', label: 'Notifications', icon: <Bell size={16} />, show: true },
    { key: 'security', label: 'Security', icon: <Shield size={16} />, show: true },
  ]
  const subtitle = isClient ? `${user?.companyName ?? ''} · ${isCustomerAdmin ? 'Company Admin' : 'Member'}` : user?.email

  return (
    <>
      <Topbar title={isClient ? 'Settings' : 'My Profile'} subtitle={subtitle} search={false} newTicket={isClient} />
      <div className="flex min-h-0 flex-1">
        <aside className="w-56 shrink-0 border-r border-line bg-white p-3">
          {tabs.filter((t) => t.show).map((t) => (
            <button key={t.key} onClick={() => setTab(t.key)} className={cn('flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium', tab === t.key ? 'bg-brand-50 text-brand-600' : 'text-gray-700 hover:bg-gray-50')}>
              {t.icon} {t.label}
            </button>
          ))}
        </aside>
        <Page>
          <div className="mx-auto max-w-3xl">
            {tab === 'profile' && <ProfileTab />}
            {tab === 'team' && user?.companyId && <TeamTab companyId={user.companyId} />}
            {tab === 'company' && user?.companyId && <CompanyTab companyId={user.companyId} />}
            {tab === 'notifications' && <NotificationsTab />}
            {tab === 'security' && <SecurityTab />}
          </div>
        </Page>
      </div>
    </>
  )
}

function SectionCard({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Card className="mb-4">
      <CardBody className="pt-5">
        <h3 className="text-base font-semibold">{title}</h3>
        {subtitle && <p className="mb-5 text-sm text-muted">{subtitle}</p>}
        {children}
      </CardBody>
    </Card>
  )
}

function ProfileTab() {
  const { user, refresh } = useAuth()
  const toast = useToast()
  const fileInput = useRef<HTMLInputElement>(null)
  const [form, setForm] = useState({ firstName: user?.firstName ?? '', lastName: user?.lastName ?? '', phone: user?.phone ?? '' })

  const save = useMutation({ mutationFn: () => profileApi.update(form), onSuccess: async () => { await refresh(); toast('Profile saved') } })
  const upload = useMutation({ mutationFn: profileApi.uploadPhoto, onSuccess: async () => { await refresh(); toast('Photo updated') }, onError: (e) => toast(errorMessage(e), 'error') })
  const fields = isApiError(save.error) ? save.error.fields ?? {} : {}
  if (!user) return null

  return (
    <SectionCard title="Profile Information" subtitle="Update your name and contact details">
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }} className="space-y-4">
        {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
        <div className="flex items-center gap-4">
          <Avatar size="xl" firstName={user.firstName} lastName={user.lastName} src={api.mediaUrl(user.photoUrl)} />
          <div>
            <div className="flex items-center gap-2"><p className="font-semibold">{user.firstName} {user.lastName}</p>{user.role === 'CUSTOMER_ADMIN' && <GenericBadge tone="brand"><Building2 size={11} className="mr-1 inline" />Company Admin</GenericBadge>}</div>
            <button type="button" onClick={() => fileInput.current?.click()} className="text-sm font-medium text-brand-600 hover:underline">Change photo</button>
            <p className="text-xs text-muted">JPG, PNG or WebP, max 5 MB</p>
            <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={(e) => e.target.files?.[0] && upload.mutate(e.target.files[0])} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Field label="First Name" error={fields.firstName}><Input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} /></Field>
          <Field label="Last Name" error={fields.lastName}><Input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} /></Field>
        </div>
        <Field label="Email Address" hint="Contact your administrator to change your email."><Input disabled value={user.email} /></Field>
        <Field label="Phone Number" error={fields.phone}><PhoneInput value={form.phone} onChange={(v) => setForm({ ...form, phone: v })} /></Field>
        <div className="flex justify-end"><Button type="submit" loading={save.isPending}><Save size={16} /> Save Changes</Button></div>
      </form>
    </SectionCard>
  )
}

function TeamTab({ companyId }: { companyId: number }) {
  const qc = useQueryClient()
  const toast = useToast()
  const { user: me } = useAuth()
  const [inviteOpen, setInviteOpen] = useState(false)
  const members = useQuery({ queryKey: ['team', companyId], queryFn: () => teamApi.members(companyId) })
  const company = useQuery({ queryKey: ['companies', companyId], queryFn: () => companiesApi.get(companyId) })
  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['team', companyId] }); void qc.invalidateQueries({ queryKey: ['companies', companyId] }) }
  const resend = useMutation({ mutationFn: (id: number) => teamApi.resendInvite(companyId, id), onSuccess: () => toast('Invitation re-sent'), onError: (e) => toast(errorMessage(e), 'error') })
  const setStatus = useMutation({ mutationFn: ({ id, s }: { id: number; s: UserStatus }) => teamApi.setStatus(companyId, id, s), onSuccess: () => { invalidate(); toast('Updated') }, onError: (e) => toast(errorMessage(e), 'error') })
  const setAdmin = useMutation({ mutationFn: ({ id, admin }: { id: number; admin: boolean }) => teamApi.setAdmin(companyId, id, admin), onSuccess: () => { invalidate(); toast('Updated') }, onError: (e) => toast(errorMessage(e), 'error') })
  const sub = company.data?.subscription
  const used = sub?.usage.activeUsers ?? 0
  const max = sub?.limits.maxUsers ?? 0

  return (
    <>
      <SectionCard title="Team Members" subtitle="People from your company who can raise and view tickets">
        {sub && (
          <div className="mb-4 rounded-lg bg-gray-50 p-4">
            <div className="flex justify-between text-sm"><span className="font-medium">Seats used</span><span>{used} / {max}</span></div>
            <ProgressBar value={used} max={max} color="#111827" className="mt-2" />
          </div>
        )}
        <div className="divide-y divide-line">
          {members.data?.map((u: User) => (
            <div key={u.id} className="flex items-center gap-3 py-3">
              <Avatar firstName={u.firstName} lastName={u.lastName} src={api.mediaUrl(u.photoUrl)} />
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-2 text-sm font-medium">{u.firstName} {u.lastName} {u.id === me?.id && <span className="text-xs text-muted">(you)</span>}</p>
                <p className="truncate text-xs text-muted">{u.email}</p>
              </div>
              {u.id === me?.id ? (
                <GenericBadge tone={u.role === 'CUSTOMER_ADMIN' ? 'brand' : 'gray'}>{u.role === 'CUSTOMER_ADMIN' ? 'Admin' : 'Member'}</GenericBadge>
              ) : (
                <label className="flex items-center gap-2 text-xs text-muted">
                  Company Admin
                  <Toggle checked={u.role === 'CUSTOMER_ADMIN'} onChange={(v) => setAdmin.mutate({ id: u.id, admin: v })} />
                </label>
              )}
              <UserStatusBadge status={u.status} />
              {u.id !== me?.id && (
                <div className="flex gap-1">
                  {u.status === 'INVITED' && <button title="Resend invitation" onClick={() => resend.mutate(u.id)} className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink"><MailCheck size={15} /></button>}
                  {u.status === 'ACTIVE' && <button title="Deactivate" onClick={() => setStatus.mutate({ id: u.id, s: 'INACTIVE' })} className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-red-600"><UserX size={15} /></button>}
                  {u.status === 'INACTIVE' && <button title="Reactivate" onClick={() => setStatus.mutate({ id: u.id, s: 'ACTIVE' })} className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-green-600"><UserCheck size={15} /></button>}
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="mt-4"><Button variant="secondary" onClick={() => setInviteOpen(true)} disabled={!!sub && used >= max}><Plus size={16} /> Invite team member</Button>{!!sub && used >= max && <span className="ml-3 text-sm text-muted">All seats are in use.</span>}</div>
      </SectionCard>
      <InviteMemberModal open={inviteOpen} onClose={() => setInviteOpen(false)} companyId={companyId} />
    </>
  )
}

function InviteMemberModal({ open, onClose, companyId }: { open: boolean; onClose: () => void; companyId: number }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [form, setForm] = useState<InviteTeamMemberRequest>({ firstName: '', lastName: '', email: '', phone: '', admin: false })
  const invite = useMutation({
    mutationFn: () => teamApi.invite(companyId, form),
    onSuccess: (u) => { void qc.invalidateQueries({ queryKey: ['team', companyId] }); void qc.invalidateQueries({ queryKey: ['companies'] }); toast(`Invitation sent to ${u.email}`); onClose(); setForm({ firstName: '', lastName: '', email: '', phone: '', admin: false }) },
  })
  const fields = isApiError(invite.error) ? invite.error.fields ?? {} : {}
  return (
    <Modal open={open} onClose={onClose} title="Invite team member" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="invite-member" loading={invite.isPending}>Send invitation</Button></>}>
      <form id="invite-member" onSubmit={(e) => { e.preventDefault(); invite.mutate() }} className="space-y-4">
        {invite.isError && <Alert>{errorMessage(invite.error)}</Alert>}
        <div className="grid grid-cols-2 gap-4">
          <Field label="First name" error={fields.firstName}><Input required value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} /></Field>
          <Field label="Last name" error={fields.lastName}><Input required value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} /></Field>
        </div>
        <Field label="Email" error={fields.email ?? (form.email && !isValidEmail(form.email) ? 'Enter a valid email address' : undefined)}><Input type="email" required value={form.email} invalid={!!form.email && !isValidEmail(form.email)} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
        <Field label="Phone" error={fields.phone}><PhoneInput value={form.phone} onChange={(v) => setForm({ ...form, phone: v })} /></Field>
        <Checkbox label="Make this person a Company Admin (can manage the team)" checked={form.admin} onChange={(e) => setForm({ ...form, admin: e.target.checked })} />
      </form>
    </Modal>
  )
}

function CompanyTab({ companyId }: { companyId: number }) {
  const company = useQuery({ queryKey: ['companies', companyId], queryFn: () => companiesApi.get(companyId) })
  const c = company.data
  if (!c) return null
  const sub = c.subscription
  return (
    <SectionCard title="Company Information" subtitle="Contact support to update company details">
      <div className="grid grid-cols-2 gap-3">
        <Item label="Company Name" value={c.name} />
        <Item label="Plan" value={sub ? (sub.subscriptionType === 'CUSTOM' ? 'Custom' : sub.planName) : '—'} />
        <Item label="Contact Email" value={c.contactEmail ?? '—'} />
        <Item label="Member Since" value={formatMonthYear(c.createdAt)} />
        {sub && <Item label="Team Seats" value={`${sub.usage.activeUsers} of ${sub.limits.maxUsers}`} />}
        {sub && <Item label="Tickets per month" value={sub.limits.monthlyTicketLimit} />}
      </div>
      {sub && (
        <div className="mt-4">
          <p className="mb-2 text-xs uppercase tracking-wide text-muted">Enabled features</p>
          <div className="flex flex-wrap gap-1.5">{sub.features.filter((f) => f.enabled).map((f) => <GenericBadge key={f.code} tone="brand">{f.name}</GenericBadge>)}</div>
        </div>
      )}
    </SectionCard>
  )
}

const Item = ({ label, value }: { label: string; value: ReactNode }) => <div className="rounded-lg bg-gray-50 px-4 py-3"><p className="text-xs text-muted">{label}</p><p className="mt-0.5 font-medium">{value}</p></div>

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button type="button" role="switch" aria-checked={checked} onClick={() => onChange(!checked)}
      className={cn('relative h-6 w-11 shrink-0 rounded-full transition-colors', checked ? 'bg-brand-600' : 'bg-gray-200')}>
      <span className={cn('absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform', checked ? 'left-0.5 translate-x-5' : 'left-0.5')} />
    </button>
  )
}

function ChannelRow({ title, sub, checked, onChange }: { title: string; sub: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center justify-between py-3">
      <div><p className="text-sm font-medium">{title}</p><p className="text-xs text-muted">{sub}</p></div>
      <Toggle checked={checked} onChange={onChange} />
    </div>
  )
}

function NotificationsTab() {
  const { user, refresh } = useAuth()
  const toast = useToast()
  const [prefs, setPrefs] = useState<NotificationPreferences>(user?.notifications ?? { email: true, browser: false, onAssign: true, onReply: true, onResolve: true })
  const save = useMutation({ mutationFn: () => profileApi.updateNotifications(prefs), onSuccess: async () => { await refresh(); toast('Preferences saved') }, onError: (e) => toast(errorMessage(e), 'error') })
  return (
    <SectionCard title="Notifications" subtitle="Choose how you want to be notified">
      <p className="text-sm font-medium">Delivery Channels</p>
      <div className="divide-y divide-line">
        <ChannelRow title="Email notifications" sub={`Receive updates at ${user?.email}`} checked={prefs.email} onChange={(v) => setPrefs({ ...prefs, email: v })} />
        <ChannelRow title="Browser notifications" sub="Push notifications in your browser" checked={prefs.browser} onChange={(v) => setPrefs({ ...prefs, browser: v })} />
      </div>
      <p className="mt-4 text-sm font-medium">Notify me when…</p>
      <div className="mt-2 flex flex-col gap-2">
        <Checkbox label="A ticket is assigned to an engineer" checked={prefs.onAssign} onChange={(e) => setPrefs({ ...prefs, onAssign: e.target.checked })} />
        <Checkbox label="Support replies to my ticket" checked={prefs.onReply} onChange={(e) => setPrefs({ ...prefs, onReply: e.target.checked })} />
        <Checkbox label="My ticket is resolved or closed" checked={prefs.onResolve} onChange={(e) => setPrefs({ ...prefs, onResolve: e.target.checked })} />
      </div>
      <div className="mt-5 flex justify-end"><Button onClick={() => save.mutate()} loading={save.isPending}><Save size={16} /> Save Preferences</Button></div>
    </SectionCard>
  )
}

function SecurityTab() {
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const change = useMutation({
    mutationFn: () => authApi.changePassword(current, next),
    onSuccess: () => { setCurrent(''); setNext(''); setConfirm(''); setError(null); toast('Password updated') },
    onError: (e) => setError(errorMessage(e)),
  })
  return (
    <SectionCard title="Change Password" subtitle="Use a strong password you don't use elsewhere">
      <form onSubmit={(e) => { e.preventDefault(); if (next !== confirm) { setError('New passwords do not match'); return } change.mutate() }} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <Field label="Current Password"><Input type="password" autoComplete="current-password" required placeholder="Enter current password" value={current} onChange={(e) => setCurrent(e.target.value)} /></Field>
        <Field label="New Password" hint="At least 10 characters with upper, lower, a digit and a symbol."><Input type="password" autoComplete="new-password" required value={next} onChange={(e) => setNext(e.target.value)} /></Field>
        <Field label="Confirm Password"><Input type="password" autoComplete="new-password" required placeholder="Repeat new password" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></Field>
        <div className="flex justify-end"><Button type="submit" loading={change.isPending}><Lock size={16} /> Update Password</Button></div>
      </form>
    </SectionCard>
  )
}
