import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ArrowLeft, BookOpen, Building2, CirclePause, Clock, FileText, Lock, Paperclip, Send, Trash2 } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Checkbox, Textarea } from '../../components/ui/Input'
import { TemplatePicker } from '../../components/ui/TemplatePicker'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { GenericBadge, PriorityBadge, SlaBadge, StatusBadge } from '../../components/ui/Badge'
import { Avatar } from '../../components/ui/Avatar'
import { Spinner } from '../../components/ui/Spinner'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { ticketsApi } from '../../api/tickets'
import { teamApi } from '../../api/users'
import { HoldModal } from './HoldModal'
import { holdReasonLabel } from './ticketOptions'
import { useAuth } from '../../auth/useAuth'
import { api, errorMessage } from '../../lib/api'
import { cn } from '../../lib/cn'
import { formatShortDate, shortName, timeAgo } from '../../lib/format'
import { priorityStyles } from '../../components/ui/badgeStyles'
import { useFeature } from '../../lib/useFeature'
import { LinkifiedText } from '../../components/ui/LinkifiedText'
import { RelatedArticlesCard } from '../kb/RelatedArticlesCard'
import { ArticlePickerModal } from '../kb/ArticlePickerModal'
import type { TicketPriority, TicketStatus } from '../../types'

const STATUS_OPTS = [{ value: 'OPEN', label: 'Open' }, { value: 'IN_PROGRESS', label: 'In Progress' }, { value: 'ON_HOLD', label: 'On Hold' }, { value: 'RESOLVED', label: 'Resolved' }, { value: 'CLOSED', label: 'Closed' }] as const

const fmtBytes = (n: number) => (n > 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`)
const overdue = (iso?: string, done?: string) => !!iso && !done && new Date(iso).getTime() < Date.now()
const dateTime = (iso: string) => `${formatShortDate(iso)} ${new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`

export default function TicketDetailPage() {
  const { id } = useParams()
  const ticketId = Number(id)
  const qc = useQueryClient()
  const toast = useToast()
  const { user, hasRole } = useAuth()
  const isStaff = hasRole('ADMIN', 'ENGINEER')
  // Staff always see the SLA clocks; customers only when their plan includes SLA Management.
  const showSla = useFeature('SLA_MANAGEMENT')
  const [comment, setComment] = useState('')
  const [internal, setInternal] = useState(false)
  const [articlePicker, setArticlePicker] = useState(false)
  const [holdOpen, setHoldOpen] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  // Poll while this page is open so new comments/status changes from the other side show up
  // without the user having to refresh. Pauses when the tab isn't visible (default react-query behaviour).
  const ticket = useQuery({ queryKey: ['tickets', ticketId], queryFn: () => ticketsApi.get(ticketId), refetchInterval: 10_000 })
  const assignees = useQuery({ queryKey: ['tickets', 'assignees'], queryFn: ticketsApi.assignees, enabled: isStaff })
  // The ticket's company people, for the Requester picker (staff only).
  const ticketCompanyId = ticket.data?.companyId
  const members = useQuery({ queryKey: ['team', ticketCompanyId], queryFn: () => teamApi.members(Number(ticketCompanyId)), enabled: isStaff && !!ticketCompanyId })
  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['tickets'] }) }
  const onErr = (e: unknown) => toast(errorMessage(e), 'error')

  const setStatus = useMutation({ mutationFn: (s: TicketStatus) => ticketsApi.setStatus(ticketId, s), onSuccess: invalidate, onError: onErr })
  const assign = useMutation({ mutationFn: (uid: number | null) => ticketsApi.assign(ticketId, uid), onSuccess: invalidate, onError: onErr })
  const setRequester = useMutation({ mutationFn: (rid: number | null) => ticketsApi.setRequester(ticketId, rid), onSuccess: () => { invalidate(); toast('Requester updated') }, onError: onErr })
  const setPriority = useMutation({ mutationFn: (p: TicketPriority) => ticketsApi.setPriority(ticketId, p), onSuccess: () => { invalidate(); toast('Priority updated — SLA targets recalculated') }, onError: onErr })
  const addComment = useMutation({ mutationFn: () => ticketsApi.addComment(ticketId, comment, internal), onSuccess: () => { setComment(''); setInternal(false); invalidate() }, onError: onErr })
  const attach = useMutation({ mutationFn: (f: File) => ticketsApi.attach(ticketId, f), onSuccess: () => { invalidate(); toast('Attachment added') }, onError: onErr })
  const removeAttachment = useMutation({ mutationFn: (mid: string) => ticketsApi.removeAttachment(ticketId, mid), onSuccess: () => { invalidate(); toast('Attachment removed') }, onError: onErr })

  if (ticket.isLoading) return <><Topbar title="Ticket" /><Page><Spinner /></Page></>
  if (!ticket.data) return <><Topbar title="Ticket" /><Page><Alert>Ticket not found, or you don't have access to it.</Alert></Page></>
  const t = ticket.data
  const closed = t.status === 'CLOSED'

  // Once resolved/closed for more than 7 days, the ticket can no longer be reopened (backend-enforced too).
  const terminalAt = t.status === 'CLOSED' ? t.closedAt : t.status === 'RESOLVED' ? t.resolvedAt : undefined
  const reopenLocked = (t.status === 'RESOLVED' || t.status === 'CLOSED') && !!terminalAt
    && Date.now() - new Date(terminalAt).getTime() > 7 * 24 * 60 * 60 * 1000

  // Only an open or in-progress ticket can be put on hold.
  const canHold = t.status === 'OPEN' || t.status === 'IN_PROGRESS' || t.status === 'ON_HOLD'
  const paused = t.resolutionSla === 'PAUSED'
  // Requester: active customer accounts of the ticket's company. A requester who has since been
  // deactivated is kept in the list so the picker can still show who it is.
  const requesterOpts = [
    { value: 0, label: '— None (company-level) —', description: "The company's admins get the updates" },
    ...(members.data ?? [])
      .filter((u) => u.status === 'ACTIVE' && (u.role === 'CUSTOMER' || u.role === 'CUSTOMER_ADMIN'))
      .map((u) => ({ value: u.id, label: `${u.firstName} ${u.lastName}`, description: u.email })),
  ]
  if (t.requester && !requesterOpts.some((o) => o.value === t.requester?.id)) {
    requesterOpts.push({ value: t.requester.id, label: `${t.requester.firstName} ${t.requester.lastName}`, description: 'No longer active' })
  }

  // Customers only get the transitions the backend allows.
  const statusOptions = (isStaff ? STATUS_OPTS.filter((o) => o.value !== 'ON_HOLD' || canHold) : STATUS_OPTS.filter((o) =>
    (t.status === 'RESOLVED' && (o.value === 'CLOSED' || o.value === 'OPEN')) || (t.status === 'CLOSED' && o.value === 'OPEN') || o.value === t.status))
    .filter((o) => !(reopenLocked && o.value === 'OPEN'))

  return (
    <>
      <Topbar title={`Ticket #${t.id}`} subtitle={`${t.companyName} · opened ${timeAgo(t.createdAt)} by ${t.createdBy.firstName} ${t.createdBy.lastName}`} />
      <Page>
        <Link to="/tickets" className="inline-flex items-center gap-1 text-sm text-muted hover:text-ink"><ArrowLeft size={14} /> Back to tickets</Link>
        <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
          <div className="space-y-4 xl:col-span-2">
            <Card className="p-6">
              <div className="flex flex-wrap items-center gap-2">
                <PriorityBadge priority={t.priority} /><StatusBadge status={t.status} /><GenericBadge>{t.category.charAt(0) + t.category.slice(1).toLowerCase()}</GenericBadge>
                {isStaff && <Link to={`/companies/${t.companyId}`} className="ml-2 inline-flex items-center gap-1.5 text-sm text-brand-600 hover:underline"><Building2 size={14} /> {t.companyName}</Link>}
              </div>
              <h2 className="mt-3 text-xl font-semibold">{t.title}</h2>
              <LinkifiedText text={t.description} className="mt-3 whitespace-pre-wrap text-sm leading-6 text-gray-700" />
              {t.stepsToReproduce && (
                <div className="mt-4 rounded-lg bg-gray-50 p-4">
                  <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">Steps to reproduce</p>
                  <p className="whitespace-pre-wrap text-sm leading-6 text-gray-700">{t.stepsToReproduce}</p>
                </div>
              )}
            </Card>

            <Card>
              <CardHeader title={`Attachments (${t.attachments.length})`} action={!closed && <Button size="sm" variant="secondary" onClick={() => fileInput.current?.click()} loading={attach.isPending}><Paperclip size={14} /> Add file</Button>} />
              <CardBody>
                <input ref={fileInput} type="file" hidden accept=".png,.jpg,.jpeg,.webp,.pdf,.csv,.txt" onChange={(e) => { const f = e.target.files?.[0]; if (f) attach.mutate(f); e.target.value = '' }} />
                {t.attachments.length === 0 ? <p className="text-sm text-muted">No files attached. PNG, JPG, WebP, PDF, CSV or TXT up to 20 MB.</p> : (
                  <ul className="divide-y divide-line">
                    {t.attachments.map((a) => (
                      <li key={a.id} className="flex items-center gap-3 py-2.5">
                        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gray-100 text-muted"><FileText size={16} /></span>
                        <a href={api.mediaUrl(a.url)} target="_blank" rel="noreferrer" className="min-w-0 flex-1 truncate text-sm font-medium text-brand-600 hover:underline">{a.name}</a>
                        <span className="text-xs text-muted">{fmtBytes(a.sizeBytes)} · {timeAgo(a.createdAt)}</span>
                        {!closed && <button onClick={() => confirm(`Remove ${a.name}?`) && removeAttachment.mutate(a.id)} className="rounded-md p-1.5 text-muted hover:bg-red-50 hover:text-red-600" aria-label="Remove"><Trash2 size={14} /></button>}
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Activity" />
              <CardBody className="space-y-4">
                {t.comments.map((c) => c.system ? (
                  <p key={c.id} className="flex items-center gap-2 pl-1 text-xs text-muted"><Clock size={12} /> {c.body} <span className="ml-auto">{timeAgo(c.createdAt)}</span></p>
                ) : (
                  <div key={c.id} className="flex gap-3">
                    <Avatar size="sm" firstName={c.author?.firstName} lastName={c.author?.lastName} src={api.mediaUrl(c.author?.photoUrl)} />
                    <div className={cn('flex-1 rounded-lg px-4 py-3', c.internal ? 'border border-yellow-200 bg-yellow-50' : 'bg-gray-50')}>
                      <div className="flex items-center justify-between text-xs text-muted">
                        <span className="flex items-center gap-2 font-medium text-ink">{c.author ? `${c.author.firstName} ${c.author.lastName}` : 'System'}{c.internal && <span className="inline-flex items-center gap-1 rounded-full bg-yellow-100 px-2 py-0.5 text-[11px] text-yellow-800"><Lock size={10} /> Internal note</span>}</span>
                        <span>{timeAgo(c.createdAt)}</span>
                      </div>
                      <LinkifiedText text={c.body} className="mt-1 whitespace-pre-wrap text-sm" />
                    </div>
                  </div>
                ))}
                {closed ? <Alert tone="info">This ticket is closed. Reopen it to add comments.</Alert> : (
                  <form onSubmit={(e) => { e.preventDefault(); addComment.mutate() }} className="flex gap-3">
                    <Avatar size="sm" firstName={user?.firstName} lastName={user?.lastName} src={api.mediaUrl(user?.photoUrl)} />
                    <div className="flex-1">
                      <Textarea value={comment} onChange={(e) => setComment(e.target.value)} placeholder={internal ? 'Internal note (customer will not see this)…' : 'Add a comment…'} className={cn('min-h-20', internal && 'border-yellow-300 bg-yellow-50/40')} />
                      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-3">
                          {isStaff ? <Checkbox label="Internal note" checked={internal} onChange={(e) => setInternal(e.target.checked)} /> : <span />}
                          {/*
                            Staff only — the backend returns an empty list for customers, so this
                            renders nothing for them regardless of what the client asks for.
                            Appends rather than replaces: a canned reply is usually the opening of
                            a message, not the whole of it.
                          */}
                          {isStaff && (
                            <TemplatePicker
                              kind="REPLY"
                              companyId={t.companyId}
                              onPick={(tpl) => setComment((prev) => (prev.trim() ? `${prev.trimEnd()}\n\n${tpl.body}` : tpl.body))}
                            />
                          )}
                          {/* Links the article to the ticket and adds a link line to the reply.
                              An article the customer can't open only goes into an internal note. */}
                          {isStaff && (
                            <button type="button" onClick={() => setArticlePicker(true)}
                              className="flex items-center gap-1.5 rounded-lg border border-line bg-surface px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50">
                              <BookOpen size={15} className="text-muted" /> Article
                            </button>
                          )}
                        </div>
                        <Button size="sm" type="submit" disabled={!comment.trim()} loading={addComment.isPending}><Send size={14} /> {internal ? 'Add note' : 'Comment'}</Button>
                      </div>
                    </div>
                  </form>
                )}
              </CardBody>
            </Card>
          </div>

          <div className="space-y-4">
            <Card>
              <CardHeader title="Details" />
              <CardBody className="space-y-4 text-sm">
                <div>
                  <p className="text-xs uppercase tracking-wide text-muted">Status</p>
                  {/* On Hold needs a reason, so it opens a dialog instead of changing the status directly. */}
                  <SelectMenu className="mt-1" value={t.status} onChange={(s) => (s === 'ON_HOLD' ? setHoldOpen(true) : setStatus.mutate(s))} options={statusOptions} disabled={statusOptions.length <= 1} />
                  {reopenLocked && <p className="mt-1.5 text-xs text-muted">Was {t.status === 'CLOSED' ? 'closed' : 'resolved'} more than 7 days ago — it can no longer be reopened.</p>}
                  {t.status === 'ON_HOLD' && (
                    <div className="mt-2 rounded-lg bg-purple-100 px-3 py-2 text-xs text-purple-700">
                      <p className="flex items-center gap-1.5 font-medium"><CirclePause size={13} /> {t.holdReasonLabel ?? holdReasonLabel(t.holdReason)}</p>
                      <p className="mt-0.5">{t.slaPausedAt && <>Put on hold {timeAgo(t.slaPausedAt)}</>}{t.holdUntil && <> · until {dateTime(t.holdUntil)}</>}</p>
                      {t.holdReason === 'CUSTOMER_REPLY' && <p className="mt-0.5">{isStaff ? 'Resumes by itself when the customer replies.' : 'Reply below and work picks up again straight away.'}</p>}
                      {isStaff && t.holdReason !== 'CUSTOMER_REPLY' && <p className="mt-0.5">Change the status to resume it.</p>}
                    </div>
                  )}
                </div>
                {isStaff && (
                  <div>
                    <p className="text-xs uppercase tracking-wide text-muted">Priority</p>
                    <SelectMenu<TicketPriority> className="mt-1" value={t.priority} onChange={(p) => setPriority.mutate(p)}
                      options={(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as TicketPriority[]).map((p) => ({ value: p, label: priorityStyles[p].label }))} />
                    <p className="mt-1.5 text-xs text-muted">Changing this re-stamps the SLA deadlines.</p>
                  </div>
                )}
                <div>
                  <p className="text-xs uppercase tracking-wide text-muted">Requester</p>
                  {isStaff ? (
                    <SelectMenu<number> className="mt-1" value={t.requester?.id ?? 0} onChange={(v) => setRequester.mutate(v === 0 ? null : v)} options={requesterOpts} disabled={closed} />
                  ) : (
                    <p className="mt-1 flex items-center gap-2">
                      {t.requester ? <><Avatar size="sm" firstName={t.requester.firstName} lastName={t.requester.lastName} src={api.mediaUrl(t.requester.photoUrl)} /> {t.requester.firstName} {t.requester.lastName}</> : <span className="italic text-gray-400">Company-level ticket</span>}
                    </p>
                  )}
                  {t.requester?.id !== t.createdBy.id && <p className="mt-1.5 text-xs text-muted">Logged by {t.createdBy.firstName} {t.createdBy.lastName}</p>}
                </div>
                <div>
                  <p className="text-xs uppercase tracking-wide text-muted">Assignee</p>
                  {isStaff ? (
                    <SelectMenu<number> className="mt-1" value={t.assignee?.id ?? 0} onChange={(v) => assign.mutate(v === 0 ? null : v)}
                      options={[{ value: 0, label: 'Unassigned' }, ...(assignees.data ?? []).map((u) => ({ value: u.id, label: `${u.firstName} ${u.lastName}`, description: u.role === 'ADMIN' ? 'Admin' : 'Engineer' }))]} />
                  ) : (
                    <p className="mt-1 flex items-center gap-2">
                      {t.assignee ? <><Avatar size="sm" firstName={t.assignee.firstName} lastName={t.assignee.lastName} src={api.mediaUrl(t.assignee.photoUrl)} /> {shortName(t.assignee.firstName, t.assignee.lastName)}</> : <span className="italic text-gray-400">Waiting for an engineer</span>}
                    </p>
                  )}
                </div>
                {showSla && (
                <div>
                  <p className="flex items-center gap-2 text-xs uppercase tracking-wide text-muted">
                    SLA <SlaBadge state={t.resolutionSla} quiet />
                  </p>
                  <ul className="mt-1 space-y-1 text-gray-700">
                    <li className={cn('flex items-center gap-2', overdue(t.responseDueAt, t.firstResponseAt) && 'text-red-600')}>
                      {overdue(t.responseDueAt, t.firstResponseAt) ? <AlertTriangle size={14} /> : <Clock size={14} className="text-muted" />}
                      First response {t.firstResponseAt ? `✓ ${timeAgo(t.firstResponseAt)}` : t.responseDueAt ? `due ${formatShortDate(t.responseDueAt)} ${new Date(t.responseDueAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : '—'}
                    </li>
                    {paused ? (
                      // The deadline isn't overdue while paused — it moves out by the paused time on resume.
                      <li className="flex items-center gap-2 text-purple-700" title={t.resolutionDueAt ? `Was due ${dateTime(t.resolutionDueAt)} — moves out by the paused time when work resumes` : undefined}>
                        <CirclePause size={14} /> Resolution paused{t.slaPausedAt ? ` ${timeAgo(t.slaPausedAt)}` : ''}
                      </li>
                    ) : (
                      <li className={cn('flex items-center gap-2', overdue(t.resolutionDueAt, t.resolvedAt) && 'text-red-600')}>
                        {overdue(t.resolutionDueAt, t.resolvedAt) ? <AlertTriangle size={14} /> : <Clock size={14} className="text-muted" />}
                        Resolution {t.resolvedAt ? `✓ ${formatShortDate(t.resolvedAt)}` : t.resolutionDueAt ? `due ${formatShortDate(t.resolutionDueAt)}` : '—'}
                      </li>
                    )}
                  </ul>
                </div>
                )}
                <div>
                  <p className="text-xs uppercase tracking-wide text-muted">Timeline</p>
                  <ul className="mt-1 space-y-1 text-gray-700">
                    <li className="flex items-center gap-2"><Clock size={14} className="text-muted" /> Created {formatShortDate(t.createdAt)}</li>
                    <li className="flex items-center gap-2"><Clock size={14} className="text-muted" /> Updated {timeAgo(t.updatedAt)}</li>
                    {t.closedAt && <li className="flex items-center gap-2"><Clock size={14} className="text-muted" /> Closed {formatShortDate(t.closedAt)}</li>}
                  </ul>
                </div>
              </CardBody>
            </Card>
            <RelatedArticlesCard ticketId={t.id} companyId={t.companyId} staff={isStaff} resolved={t.status === 'RESOLVED' || t.status === 'CLOSED'} />
          </div>
        </div>
      </Page>
      {isStaff && <HoldModal ticketId={t.id} open={holdOpen} onClose={() => setHoldOpen(false)} />}
      {articlePicker && (
        <ArticlePickerModal ticketId={t.id} companyId={t.companyId} internal={internal}
          onInsert={(line) => setComment((prev) => (prev.trim() ? `${prev.trimEnd()}\n\n${line}` : line))}
          onClose={() => setArticlePicker(false)} />
      )}
    </>
  )
}
