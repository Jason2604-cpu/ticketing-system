import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Calendar, Globe, Mail, Plus, Settings2, Upload, Users } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { MiniStat } from '../../components/ui/StatCard'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Avatar, CompanyMark } from '../../components/ui/Avatar'
import { GenericBadge, PlanStatusBadge, PriorityBadge, StatusBadge } from '../../components/ui/Badge'
import { ProgressBar, EmptyState, Alert } from '../../components/ui/Misc'
import { PlanUsageRows } from '../../components/ui/PlanUsage'
import { Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/useToast'
import { NewTicketModal } from '../tickets/NewTicketModal'
import { SubscriptionEditor } from './SubscriptionEditor'
import { BusinessHoursEditor, businessHoursValid, defaultBusinessHours } from '../../components/ui/BusinessHoursEditor'
import { companiesApi } from '../../api/companies'
import { teamApi, usersApi } from '../../api/users'
import { ticketsApi } from '../../api/tickets'
import { api, errorMessage } from '../../lib/api'
import { formatMonthYear, formatShortDate, minutesToHuman, shortName } from '../../lib/format'
import { useAuth } from '../../auth/useAuth'
import type { BusinessHours, Company, CompanySubscription, CompanySubscriptionRequest, InviteTeamMemberRequest } from '../../types'
import { Checkbox, Field, Input, Select } from '../../components/ui/Input'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { isValidEmail } from '../../lib/countries'
import { isApiError } from '../../lib/api'

export default function CompanyDetailPage() {
  const { id } = useParams()
  const companyId = Number(id)
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const qc = useQueryClient()
  const toast = useToast()
  const logoInput = useRef<HTMLInputElement>(null)
  const [newTicket, setNewTicket] = useState(false)
  const [editSub, setEditSub] = useState(false)
  const [subDraft, setSubDraft] = useState<CompanySubscriptionRequest>({ subscriptionType: 'PREDEFINED' })

  const company = useQuery({ queryKey: ['companies', companyId], queryFn: () => companiesApi.get(companyId) })
  const contacts = useQuery({ queryKey: ['team', companyId], queryFn: () => teamApi.members(companyId) })
  const engineers = useQuery({ queryKey: ['company-engineers', companyId], queryFn: () => teamApi.engineers(companyId) })
  // Filtered server-side (the same companyId filter the tickets page uses) instead of downloading
  // every ticket and filtering here.
  const tickets = useQuery({ queryKey: ['tickets', { companyId }], queryFn: () => ticketsApi.list({ companyId }) })
  const mine = tickets.data ?? []

  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['companies'] }) }
  const uploadLogo = useMutation({ mutationFn: (f: File) => companiesApi.uploadLogo(companyId, f), onSuccess: () => { invalidate(); toast('Logo updated') }, onError: (e) => toast(errorMessage(e), 'error') })
  const saveSub = useMutation({
    mutationFn: () => companiesApi.assignSubscription(companyId, subDraft),
    onSuccess: () => { invalidate(); setEditSub(false); toast('Subscription updated') },
  })

  if (company.isLoading) return <><Topbar title="Company" /><Page><Spinner /></Page></>
  if (!company.data) return <><Topbar title="Company" /><Page><p className="text-muted">Company not found.</p></Page></>
  const c = company.data
  const sub = c.subscription

  const openEdit = () => {
    setSubDraft(sub
      ? { subscriptionType: sub.subscriptionType, planId: sub.planId, featureCodes: sub.features.filter((f) => f.enabled).map((f) => f.code), limits: sub.limits, sla: sub.sla }
      : { subscriptionType: 'PREDEFINED' })
    setEditSub(true)
  }

  const count = (s: string[]) => mine.filter((t) => s.includes(t.status)).length
  const seatPct = sub ? Math.min(100, (sub.usage.activeUsers / sub.limits.maxUsers) * 100) : 0

  return (
    <>
      <Topbar title={c.name} />
      <Page>
        <Card className="p-6">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-5">
              <div className="relative">
                <CompanyMark name={c.name} src={api.mediaUrl(c.logoUrl)} size="xl" />
                {isAdmin && (
                  <>
                    <button onClick={() => logoInput.current?.click()} className="absolute -bottom-1 -right-1 rounded-full border border-line bg-surface p-1.5 text-muted shadow-sm hover:text-ink" aria-label="Upload logo"><Upload size={12} /></button>
                    <input ref={logoInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={(e) => e.target.files?.[0] && uploadLogo.mutate(e.target.files[0])} />
                  </>
                )}
              </div>
              <div>
                <div className="flex items-center gap-3">
                  <h2 className="text-2xl font-semibold">{c.name}</h2>
                  {sub && <GenericBadge tone="purple">{sub.subscriptionType === 'CUSTOM' ? 'Custom Plan' : sub.planName}</GenericBadge>}
                  {sub && sub.status !== 'ACTIVE' && <PlanStatusBadge status={sub.status} />}
                </div>
                <div className="mt-1.5 flex flex-wrap items-center gap-4 text-sm text-muted">
                  {c.contactEmail && <span className="flex items-center gap-1.5"><Globe size={14} /> {c.contactEmail}</span>}
                  <span className="flex items-center gap-1.5"><Users size={14} /> {sub ? `${sub.usage.activeUsers} of ${sub.limits.maxUsers} seats` : `${contacts.data?.length ?? 0} contacts`}</span>
                  <span className="flex items-center gap-1.5"><Calendar size={14} /> Client since {formatMonthYear(c.createdAt)}</span>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {c.contactEmail && <a href={`mailto:${c.contactEmail}`}><Button variant="secondary"><Mail size={16} /> Email</Button></a>}
              {isAdmin && <Button variant="secondary" onClick={openEdit}><Settings2 size={16} /> Subscription</Button>}
              <Button onClick={() => setNewTicket(true)}><Plus size={16} /> New Ticket</Button>
            </div>
          </div>
        </Card>

        <div className="mt-4 grid grid-cols-2 gap-4 md:grid-cols-5">
          <MiniStat label="Total Tickets" value={mine.length} />
          <MiniStat label="Open" value={count(['OPEN'])} className="text-blue-600" />
          <MiniStat label="In Progress" value={count(['IN_PROGRESS', 'ON_HOLD'])} className="text-orange-500" />
          <MiniStat label="Resolved" value={count(['RESOLVED', 'CLOSED'])} className="text-green-600" />
          <MiniStat label="Seats used" value={sub ? `${Math.round(seatPct)}%` : '—'} className={seatPct > 90 ? 'text-red-500' : 'text-amber-500'} />
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
          <Card className="xl:col-span-2">
            <CardHeader title={`Tickets from ${c.name}`} action={<Link to={`/tickets?companyId=${c.id}`} className="text-sm font-medium text-brand-600 hover:underline">View in table →</Link>} />
            <CardBody className="divide-y divide-line">
              {mine.slice(0, 6).map((t) => (
                <Link key={t.id} to={`/tickets/${t.id}`} className="flex items-center gap-4 py-3 hover:bg-gray-50">
                  <span className="w-12 font-mono text-xs text-muted">#{t.id}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{t.title}</p>
                    <p className="text-sm text-muted">{t.assignee ? shortName(t.assignee.firstName, t.assignee.lastName) : 'Unassigned'} · {formatShortDate(t.createdAt)}</p>
                  </div>
                  <PriorityBadge priority={t.priority} /><StatusBadge status={t.status} />
                </Link>
              ))}
              {mine.length === 0 && <EmptyState title="No tickets yet" hint="Tickets raised for this company will appear here." />}
            </CardBody>
          </Card>

          <div className="space-y-4">
            <Card>
              <CardHeader title="Contacts" />
              <CardBody className="space-y-3">
                {contacts.data?.map((u) => (
                  <div key={u.id} className="flex items-center gap-3">
                    <Avatar firstName={u.firstName} lastName={u.lastName} src={api.mediaUrl(u.photoUrl)} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{u.firstName} {u.lastName}</p>
                      <p className="truncate text-xs text-muted">{u.email}</p>
                    </div>
                    <GenericBadge>{u.role === 'CUSTOMER_ADMIN' ? 'Admin' : 'User'}</GenericBadge>
                  </div>
                ))}
                {contacts.data?.length === 0 && <p className="text-sm text-muted">No contacts yet — invite a Company Admin so they can manage their own team.</p>}
                {isAdmin && <InviteContact companyId={c.id} />}
                {engineers.data && engineers.data.length > 0 && (
                  <div className="border-t border-line pt-3">
                    <p className="mb-2 text-xs uppercase tracking-wide text-muted">Assigned engineers</p>
                    <div className="flex -space-x-2">{engineers.data.map((e) => <Avatar key={e.id} size="sm" firstName={e.firstName} lastName={e.lastName} src={api.mediaUrl(e.photoUrl)} className="ring-2 ring-surface" />)}</div>
                  </div>
                )}
              </CardBody>
            </Card>

            {isAdmin && <MainEngineerCard company={c} />}
            {isAdmin && <BusinessHoursCard company={c} />}

            {sub && <PlanUsageCard sub={sub} />}

            <Card>
              <CardHeader title="SLA Summary" />
              <CardBody className="space-y-3">
                {sub ? sub.sla.map((row) => (
                  <div key={row.priority}>
                    <div className="flex justify-between text-sm"><span>{row.priority.charAt(0) + row.priority.slice(1).toLowerCase()} response</span><span className="font-semibold">{minutesToHuman(row.responseTimeMinutes)}</span></div>
                    <ProgressBar value={row.responseTimeMinutes} max={row.resolutionTimeMinutes} color={row.priority === 'CRITICAL' ? '#ef4444' : row.priority === 'HIGH' ? '#f97316' : '#22c55e'} className="mt-1.5" />
                    <p className="mt-1 text-xs text-muted">Resolve within {minutesToHuman(row.resolutionTimeMinutes)}</p>
                  </div>
                )) : <Alert tone="warning">No subscription configured.</Alert>}
                {sub && (
                  <div className="border-t border-line pt-3 text-sm">
                    <p className="text-xs uppercase tracking-wide text-muted">Enabled features</p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {sub.features.filter((f) => f.enabled).map((f) => <GenericBadge key={f.code} tone="brand">{f.name}</GenericBadge>)}
                    </div>
                  </div>
                )}
              </CardBody>
            </Card>
          </div>
        </div>
      </Page>

      <NewTicketModal open={newTicket} onClose={() => setNewTicket(false)} />
      <Modal open={editSub} onClose={() => setEditSub(false)} title="Company Subscription" size="xl"
        footer={<><Button variant="secondary" onClick={() => setEditSub(false)}>Cancel</Button><Button onClick={() => saveSub.mutate()} loading={saveSub.isPending}>Save subscription</Button></>}>
        {saveSub.isError && <Alert className="mb-4">{errorMessage(saveSub.error)}</Alert>}
        <SubscriptionEditor value={subDraft} onChange={setSubDraft} />
      </Modal>
    </>
  )
}

/** Admin-only: pick which engineer/admin is notified whenever a new ticket comes in for this company. */
/**
 * What the company has consumed against its plan's limits. Storage is the one the backend
 * enforces on upload, so it's shown first and warns before an upload starts failing.
 */
function PlanUsageCard({ sub }: { sub: CompanySubscription }) {
  return (
    <Card>
      <CardHeader title="Plan Usage" action={<span className="text-sm text-muted">{sub.subscriptionType === 'CUSTOM' ? 'Custom Plan' : sub.planName ?? 'No plan'}</span>} />
      <CardBody>
        <PlanUsageRows sub={sub} />
      </CardBody>
    </Card>
  )
}

/**
 * Business hours for an existing company. Read-only summary until Edit is pressed, because this is
 * a seven-row form and the page is mostly for scanning.
 *
 * Saving affects tickets raised from then on: existing deadlines are absolute timestamps stamped
 * at creation, so changing the schedule never silently re-dates work already in flight.
 */
function BusinessHoursCard({ company }: { company: Company }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState(false)
  const [rows, setRows] = useState<BusinessHours[]>(company.businessHours ?? defaultBusinessHours())
  const [enabled, setEnabled] = useState(company.businessHoursEnabled ?? true)

  const save = useMutation({
    mutationFn: () => companiesApi.update(company.id, {
      name: company.name,
      address: company.address,
      contactEmail: company.contactEmail,
      contactPhone: company.contactPhone,
      mainEngineerId: company.mainEngineer?.id,
      businessHoursEnabled: enabled,
      businessHours: rows,
    }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['companies'] }); toast('Business hours updated'); setEditing(false) },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const startEditing = () => {
    setRows(company.businessHours ?? defaultBusinessHours())
    setEnabled(company.businessHoursEnabled ?? true)
    setEditing(true)
  }

  const summary = company.businessHours ?? defaultBusinessHours()
  const valid = businessHoursValid(rows)

  return (
    <Card>
      <CardHeader
        title="Business Hours"
        action={!editing && <button onClick={startEditing} className="text-sm font-medium text-brand-600 hover:underline">Edit</button>}
      />
      <CardBody>
        {editing ? (
          <div className="space-y-4">
            <BusinessHoursEditor value={rows} onChange={setRows} enabled={enabled} onEnabledChange={setEnabled} />
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setEditing(false)}>Cancel</Button>
              <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!valid}>Save</Button>
            </div>
          </div>
        ) : (
          <div className="space-y-2 text-sm">
            {!(company.businessHoursEnabled ?? true) && (
              <Alert tone="info">The SLA clock runs 24/7 for this company.</Alert>
            )}
            {summary.map((r) => (
              <div key={r.dayOfWeek} className="flex justify-between">
                <span className="text-muted">{r.dayOfWeek.charAt(0) + r.dayOfWeek.slice(1).toLowerCase()}</span>
                <span className={r.closed ? 'text-gray-400' : 'font-medium'}>
                  {r.closed ? 'Closed' : `${r.openTime} – ${r.closeTime}`}
                </span>
              </div>
            ))}
          </div>
        )}
      </CardBody>
    </Card>
  )
}

function MainEngineerCard({ company }: { company: Company }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(company.mainEngineer ? String(company.mainEngineer.id) : '')
  const staff = useQuery({ queryKey: ['users', 'all'], queryFn: () => usersApi.list() })
  const engineerOptions = (staff.data ?? []).filter((u) => u.role === 'ENGINEER' || u.role === 'ADMIN')

  const save = useMutation({
    mutationFn: () => companiesApi.update(company.id, {
      name: company.name,
      address: company.address,
      contactEmail: company.contactEmail,
      contactPhone: company.contactPhone,
      mainEngineerId: value ? Number(value) : undefined,
    }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['companies'] }); toast('Main engineer updated'); setEditing(false) },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  return (
    <Card>
      <CardHeader title="Main Engineer" action={!editing && <button onClick={() => { setValue(company.mainEngineer ? String(company.mainEngineer.id) : ''); setEditing(true) }} className="text-sm font-medium text-brand-600 hover:underline">Edit</button>} />
      <CardBody>
        {editing ? (
          <div className="space-y-3">
            <Select value={value} onChange={(e) => setValue(e.target.value)}>
              <option value="">None</option>
              {engineerOptions.map((u) => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
            </Select>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setEditing(false)}>Cancel</Button>
              <Button onClick={() => save.mutate()} loading={save.isPending}>Save</Button>
            </div>
          </div>
        ) : company.mainEngineer ? (
          <div className="flex items-center gap-3">
            <Avatar firstName={company.mainEngineer.firstName} lastName={company.mainEngineer.lastName} src={api.mediaUrl(company.mainEngineer.photoUrl)} />
            <div>
              <p className="text-sm font-medium">{company.mainEngineer.firstName} {company.mainEngineer.lastName}</p>
              <p className="text-xs text-muted">Notified on every new ticket</p>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted">No main engineer set — no one is auto-notified on new tickets.</p>
        )}
      </CardBody>
    </Card>
  )
}

/** Admin inviting a contact into a customer company (same seat-limited flow a Company Admin uses). */
function InviteContact({ companyId }: { companyId: number }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<InviteTeamMemberRequest>({ firstName: '', lastName: '', email: '', phone: '', admin: true })
  const invite = useMutation({
    mutationFn: () => teamApi.invite(companyId, form),
    onSuccess: (u) => { void qc.invalidateQueries({ queryKey: ['team', companyId] }); void qc.invalidateQueries({ queryKey: ['companies'] }); toast(`Invitation sent to ${u.email}`); setOpen(false) },
  })
  const fields = isApiError(invite.error) ? invite.error.fields ?? {} : {}
  return (
    <>
      <button onClick={() => setOpen(true)} className="inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:underline"><Plus size={14} /> Add contact</button>
      <Modal open={open} onClose={() => setOpen(false)} title="Invite contact" footer={<><Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" form="invite-contact" loading={invite.isPending}>Send invitation</Button></>}>
        <form id="invite-contact" onSubmit={(e) => { e.preventDefault(); invite.mutate() }} className="space-y-4">
          {invite.isError && <Alert>{errorMessage(invite.error)}</Alert>}
          <div className="grid grid-cols-2 gap-4">
            <Field label="First name" error={fields.firstName}><Input required value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} /></Field>
            <Field label="Last name" error={fields.lastName}><Input required value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} /></Field>
          </div>
          <Field label="Email" error={fields.email ?? (form.email && !isValidEmail(form.email) ? 'Enter a valid email address' : undefined)}><Input type="email" required value={form.email} invalid={!!form.email && !isValidEmail(form.email)} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
          <Field label="Phone" error={fields.phone}><PhoneInput value={form.phone} onChange={(v) => setForm({ ...form, phone: v })} /></Field>
          <Checkbox label="Company Admin (can manage their own team)" checked={form.admin} onChange={(e) => setForm({ ...form, admin: e.target.checked })} />
        </form>
      </Modal>
    </>
  )
}
