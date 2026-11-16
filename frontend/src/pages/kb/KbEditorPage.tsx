import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, ArrowLeft, BookOpen, ExternalLink, Globe, History, Lock, Save, Send, Trash2, Undo2 } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Field, Input, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { MultiSelectMenu } from '../../components/ui/MultiSelectMenu'
import { Modal } from '../../components/ui/Modal'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { kbApi, ticketArticlesApi } from '../../api/kb'
import { ticketsApi } from '../../api/tickets'
import { companiesApi } from '../../api/companies'
import { errorMessage, isApiError } from '../../lib/api'
import { formatShortDate, timeAgo } from '../../lib/format'
import { cn } from '../../lib/cn'
import { useAuth } from '../../auth/useAuth'
import type { KbArticleRequest, KbManageArticle, KbVisibility } from '../../types'
import { useKbAccess } from './kbAccess'
import { ChangesBadge, RequestedBadge, StatusBadge } from './KbBadges'
import { MarkdownEditor } from './MarkdownEditor'
import { KbAttachmentsCard } from './KbAttachmentsCard'
import { attachmentMarkdown, draftFromTicket } from './kbMarkdown'

interface Form {
  title: string
  summary: string
  body: string
  categoryId: number
  visibility: KbVisibility
  companyIds: number[]
}

const toForm = (a?: KbManageArticle | null): Form => ({
  title: a?.title ?? '',
  summary: a?.summary ?? '',
  body: a?.body ?? '',
  categoryId: a?.category?.id ?? 0,
  visibility: a?.visibility ?? 'INTERNAL',
  companyIds: (a?.companies ?? []).map((c) => c.id).sort((x, y) => x - y),
})

const toRequest = (f: Form): KbArticleRequest => ({
  title: f.title.trim(),
  summary: f.summary.trim() || undefined,
  body: f.body,
  categoryId: f.categoryId || null,
  visibility: f.visibility,
  companyIds: f.companyIds,
})

// Compares what would be saved, so trailing spaces the server trims don't count as an unsaved change.
const sameForm = (a: Form, b: Form) =>
  a.title.trim() === b.title.trim() && a.summary.trim() === b.summary.trim() && a.body.trimEnd() === b.body.trimEnd()
  && a.categoryId === b.categoryId && a.visibility === b.visibility
  && [...a.companyIds].sort((x, y) => x - y).join(',') === [...b.companyIds].sort((x, y) => x - y).join(',')

/** /admin/kb/new and /admin/kb/:id/edit. */
export default function KbEditorPage() {
  const { id } = useParams()
  const articleId = id ? Number(id) : null
  const { hasRole } = useAuth()
  // "Write an article from this ticket" lands here as /admin/kb/new?fromTicket=<id>.
  const [params] = useSearchParams()
  const fromTicket = articleId == null && params.get('fromTicket') ? Number(params.get('fromTicket')) : null
  const ticket = useQuery({
    queryKey: ['tickets', fromTicket],
    queryFn: () => ticketsApi.get(fromTicket!),
    enabled: fromTicket != null && hasRole('ADMIN', 'ENGINEER'),
    refetchInterval: false,
    retry: false,
  })
  const article = useQuery({
    queryKey: ['kb', 'manage', 'article', articleId],
    queryFn: () => kbApi.manageGet(articleId!),
    enabled: articleId != null,
    refetchOnWindowFocus: false,
    retry: (n, e) => !(isApiError(e) && (e.status === 403 || e.status === 404)) && n < 1,
  })

  if (articleId != null && (article.isLoading || article.isError)) {
    return (
      <>
        <Topbar title="Edit article" search={false} newTicket={false} />
        <Page>
          {article.isError ? (
            <EmptyState icon={<BookOpen size={22} />} title="Article not found"
              hint={isApiError(article.error) && article.error.status === 404
                ? 'It may have been deleted, or it is pinned to a company you aren’t assigned to.'
                : errorMessage(article.error)}
              action={<Link to="/admin/kb"><Button variant="secondary">All articles</Button></Link>} />
          ) : <Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card>}
        </Page>
      </>
    )
  }
  if (fromTicket != null && ticket.isLoading) {
    return <><Topbar title="New article" search={false} newTicket={false} /><Page><Card><div className="p-10 text-center text-sm text-muted">Loading the ticket…</div></Card></Page></>
  }
  // Keyed so switching from /new to /:id after the first save (or between articles) starts fresh.
  return <Editor key={articleId ?? 'new'} initial={article.data ?? null}
    prefill={ticket.data ? draftFromTicket(ticket.data) : null} fromTicketId={ticket.data ? fromTicket : null} />
}

function Editor({ initial, prefill, fromTicketId }: {
  initial: KbManageArticle | null
  prefill: { title: string; body: string } | null
  fromTicketId: number | null
}) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { user } = useAuth()
  const { isManager, base } = useKbAccess()

  const [current, setCurrent] = useState<KbManageArticle | null>(initial)
  const [form, setForm] = useState<Form>(() => ({ ...toForm(initial), ...(prefill ?? {}) }))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [modal, setModal] = useState<'publish' | 'decline' | null>(null)

  const categories = useQuery({ queryKey: ['kb', 'manage', 'categories'], queryFn: kbApi.manageCategories })
  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list })

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }))
  const dirty = useMemo(() => !sameForm(form, toForm(current)), [form, current])

  // A browser-level guard for unsaved work (closing the tab, reloading).
  useEffect(() => {
    if (!dirty) return
    const onBefore = (e: BeforeUnloadEvent) => { e.preventDefault() }
    window.addEventListener('beforeunload', onBefore)
    return () => window.removeEventListener('beforeunload', onBefore)
  }, [dirty])

  const status = current?.status ?? 'DRAFT'
  const archived = status === 'ARCHIVED'
  const ever = current?.publishedVersion != null
  const perms = current?.permissions
  const canEdit = perms?.canEdit ?? true
  // Something new for readers: never published, unpublished changes on the server, or edits here.
  const pending = !ever || !!current?.hasUnpublishedChanges || dirty
  const canPublish = isManager && canEdit && !archived && pending
  const canRequest = !isManager && canEdit && !archived && pending

  /** First save of an article written from a ticket: link the two (once). */
  const linkedTicket = useRef(false)
  const afterCreate = (a: KbManageArticle) => {
    if (!fromTicketId || linkedTicket.current) return
    linkedTicket.current = true
    ticketArticlesApi.link(fromTicketId, a.id)
      .then(() => { void qc.invalidateQueries({ queryKey: ['tickets', fromTicketId, 'articles'] }); toast(`Linked to ticket #${fromTicketId}`) })
      .catch(() => { linkedTicket.current = false })
  }

  /**
   * Uploading needs an article to attach to: a brand-new one is saved as a draft first (once,
   * even if several files arrive together). The URL stays /new until the next save.
   */
  const creating = useRef<Promise<KbManageArticle> | null>(null)
  const ensureSaved = (): Promise<KbManageArticle> => {
    if (current) return Promise.resolve(current)
    if (form.title.trim().length < 4) {
      return Promise.reject(new Error('give the article a title first — it’s saved as a draft so the file has somewhere to live'))
    }
    creating.current ??= kbApi.create(toRequest(form)).then((a) => {
      setCurrent(a)
      qc.setQueryData(['kb', 'manage', 'article', a.id], a)
      void qc.invalidateQueries({ queryKey: ['kb', 'manage', 'list'] })
      afterCreate(a)
      return a
    }).catch((e) => { creating.current = null; throw e })
    return creating.current
  }
  const uploadFile = async (file: File) => {
    const a = await ensureSaved()
    const att = await kbApi.upload(a.id, file)
    void qc.invalidateQueries({ queryKey: ['kb', 'manage', 'attachments', a.id] })
    // A retry that worked: drop the earlier "Couldn’t upload …" banner instead of leaving it up.
    setError((e) => (e?.startsWith('Couldn’t upload') ? null : e))
    return attachmentMarkdown(att)
  }
  const appendToBody = (markdown: string) => setForm((f) => ({ ...f, body: `${f.body.trimEnd()}${f.body.trim() ? '\n\n' : ''}${markdown}\n` }))

  const accept = (a: KbManageArticle, message?: string) => {
    setCurrent(a)
    setForm(toForm(a))
    setError(null)
    setFieldErrors({})
    qc.setQueryData(['kb', 'manage', 'article', a.id], a)
    void qc.invalidateQueries({ queryKey: ['kb'], refetchType: 'none' })
    void qc.invalidateQueries({ queryKey: ['kb', 'manage', 'list'] })
    void qc.invalidateQueries({ queryKey: ['kb', 'manage', 'requested-count'] })
    if (message) toast(message)
    if (!initial) { afterCreate(a); navigate(`/admin/kb/${a.id}/edit`, { replace: true }) }
  }
  const fail = (e: unknown) => {
    setError(errorMessage(e))
    setFieldErrors(isApiError(e) ? e.fields ?? {} : {})
  }

  const validate = (forPublish: boolean) => {
    const t = form.title.trim()
    if (t.length < 4) { setError('Give the article a title of at least 4 characters'); return false }
    if (forPublish && !form.body.trim()) { setError('Write the article before publishing it'); return false }
    return true
  }

  /** Saves the working copy if there is anything to save, and returns the up-to-date article. */
  const persist = async (): Promise<KbManageArticle> => {
    if (current && !dirty) return current
    const body = toRequest(form)
    return current ? kbApi.update(current.id, body) : kbApi.create(body)
  }

  const save = useMutation({ mutationFn: persist, onSuccess: (a) => accept(a, initial || current ? 'Draft saved' : 'Draft created'), onError: fail })
  const publish = useMutation({
    mutationFn: async (note: string) => { const a = await persist(); setCurrent(a); return kbApi.publish(a.id, note.trim() || undefined) },
    onSuccess: (a) => { setModal(null); accept(a, `Published — readers now see version ${a.publishedVersion}`) },
    onError: (e) => { setModal(null); fail(e) },
  })
  const request = useMutation({
    mutationFn: async () => { const a = await persist(); setCurrent(a); return kbApi.requestPublish(a.id) },
    onSuccess: (a) => accept(a, 'Sent for publishing — Admins and Knowledge Base managers have been notified'),
    onError: fail,
  })
  const decline = useMutation({
    mutationFn: (note: string) => kbApi.declinePublish(current!.id, note.trim() || undefined),
    onSuccess: (a) => { setModal(null); accept(a, 'Request declined — the engineer who asked has been notified') },
    onError: (e) => { setModal(null); fail(e) },
  })
  const archive = useMutation({
    mutationFn: () => (archived ? kbApi.unarchive(current!.id) : kbApi.archive(current!.id)),
    onSuccess: (a) => accept(a, a.status === 'ARCHIVED' ? 'Archived — readers no longer see it' : 'Restored from the archive'),
    onError: fail,
  })
  const remove = useMutation({
    mutationFn: () => kbApi.remove(current!.id),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['kb'] }); toast('Draft deleted'); navigate('/admin/kb') },
    onError: fail,
  })
  const busy = save.isPending || publish.isPending || request.isPending || decline.isPending || archive.isPending || remove.isPending

  const requestedByMe = current?.publishRequestedBy?.id === user?.id

  return (
    <>
      <Topbar
        title={current ? 'Edit article' : 'New article'}
        subtitle={current ? current.title : 'Saved as a draft — nobody else sees it until it’s published'}
        search={false}
        newTicket={false}
        actions={<Link to="/admin/kb"><Button variant="secondary"><ArrowLeft size={16} /> All articles</Button></Link>}
      />
      <Page>
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
          <div className="min-w-0 space-y-4">
            {error && <Alert>{error}</Alert>}
            {fromTicketId && !current && (
              <Alert tone="info">Drafted from <Link to={`/tickets/${fromTicketId}`} className="font-medium underline">ticket #{fromTicketId}</Link> — edit it into general steps (no customer names). It’s linked to the ticket when you save.</Alert>
            )}
            {archived && <Alert tone="warning">This article is archived: readers and suggestions don’t show it. {isManager ? 'Unarchive it to make it readable again.' : ''}</Alert>}
            {current?.publishRequestedAt && !archived && (
              <Alert tone="info">
                {isManager
                  ? <><strong>{current.publishRequestedBy?.name ?? 'An engineer'}</strong> asked for this to be published {timeAgo(current.publishRequestedAt)}. Review it, then publish or decline.</>
                  : <>{requestedByMe ? 'You' : current.publishRequestedBy?.name ?? 'Someone'} asked for this to be published {timeAgo(current.publishRequestedAt)}. An Admin or Knowledge Base manager will review it.</>}
              </Alert>
            )}

            <Card>
              <CardBody className="space-y-4 pt-5">
                <Field label="Title" error={fieldErrors.title}>
                  <Input value={form.title} onChange={(e) => set('title', e.target.value)} maxLength={200} disabled={!canEdit}
                    placeholder="Printer shows offline after a Windows update" className="h-11 text-base font-medium" invalid={!!fieldErrors.title} />
                </Field>
                <Field label="Summary" hint={`One or two sentences shown in lists, search results and ticket suggestions · ${form.summary.length}/300`} error={fieldErrors.summary}>
                  <Textarea value={form.summary} onChange={(e) => set('summary', e.target.value)} maxLength={300} rows={2} disabled={!canEdit}
                    style={{ minHeight: '4.5rem' }} placeholder="What problem this solves, in the words a customer would use." />
                </Field>
              </CardBody>
            </Card>

            <MarkdownEditor value={form.body} onChange={(v) => set('body', v)} base={base} disabled={!canEdit} invalid={!!fieldErrors.body}
              onUpload={uploadFile} onUploadError={(m) => setError(`Couldn’t upload ${m}`)}
              placeholder={'## Symptoms\n\nWhat the user sees.\n\n## Fix\n\n1. First step\n2. Second step'} />
          </div>

          <div className="space-y-4 xl:sticky xl:top-0 xl:self-start">
            <Card>
              <CardHeader title="Publishing" />
              <CardBody className="space-y-4">
                <div className="flex flex-wrap gap-1.5">
                  <StatusBadge status={status} version={current?.publishedVersion} />
                  {current?.publishRequestedAt && <RequestedBadge />}
                  {current?.hasUnpublishedChanges && <ChangesBadge />}
                </div>
                <p className="text-sm text-muted">
                  {ever
                    ? <>Readers see version {current!.publishedVersion}, published {current!.publishedAt ? formatShortDate(current!.publishedAt) : ''}.{current!.hasUnpublishedChanges ? ' The draft below has changes they don’t see yet.' : ''}</>
                    : 'Not published yet — only writers can see it.'}
                  {dirty && <span className="mt-1 block font-medium text-amber-700">Unsaved changes</span>}
                </p>

                <div className="space-y-2">
                  <Button variant="secondary" className="w-full" onClick={() => validate(false) && save.mutate()} disabled={!canEdit || !dirty || busy} loading={save.isPending}>
                    <Save size={16} /> Save draft
                  </Button>
                  {isManager ? (
                    <Button className="w-full" onClick={() => validate(true) && setModal('publish')} disabled={!canPublish || busy} loading={publish.isPending}>
                      <Globe size={16} /> {dirty ? 'Save & publish…' : 'Publish…'}
                    </Button>
                  ) : (
                    <Button className="w-full" onClick={() => validate(true) && request.mutate()} disabled={!canRequest || busy} loading={request.isPending}>
                      <Send size={16} /> {current?.publishRequestedAt ? 'Request publish again' : 'Request publish'}
                    </Button>
                  )}
                  {isManager && current?.publishRequestedAt && !archived && (
                    <Button variant="secondary" className="w-full" onClick={() => setModal('decline')} disabled={busy}>
                      <Undo2 size={16} /> Decline request
                    </Button>
                  )}
                </div>
                {!isManager && (
                  <p className="text-xs text-muted">Only an Admin or a Knowledge Base manager can publish. Requesting sends them the current draft.</p>
                )}

                {ever && (
                  <div className="flex flex-wrap gap-x-4 gap-y-1 border-t border-line pt-3 text-sm">
                    {status !== 'ARCHIVED' && (
                      <Link to={`/kb/${current!.slug}`} className="inline-flex items-center gap-1 font-medium text-brand-600 hover:underline"><ExternalLink size={14} /> View published</Link>
                    )}
                    <Link to={`/admin/kb/${current!.id}/history`} className="inline-flex items-center gap-1 font-medium text-brand-600 hover:underline"><History size={14} /> History</Link>
                  </div>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Who can read it" />
              <CardBody className="space-y-4">
                <div className="grid grid-cols-1 gap-2">
                  <VisibilityOption active={form.visibility === 'INTERNAL'} disabled={!canEdit} onClick={() => set('visibility', 'INTERNAL')}
                    icon={<Lock size={16} />} title="Internal" text="Staff only — runbooks, internal notes." />
                  <VisibilityOption active={form.visibility === 'PUBLIC'} disabled={!canEdit} onClick={() => set('visibility', 'PUBLIC')}
                    icon={<Globe size={16} />} title="Public" text="Customers can read it in the Help Centre (if their plan includes it)." />
                </div>
                <Field label="Available to" hint={isManager ? 'Leave empty for every company, or pin it to the ones it applies to' : 'Leave empty for every company. You can only pin it to companies you’re assigned to.'}>
                  <MultiSelectMenu
                    values={form.companyIds}
                    onChange={(v) => set('companyIds', v)}
                    emptyLabel="All companies"
                    disabled={!canEdit}
                    options={(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
                    searchPlaceholder="Search companies…"
                  />
                </Field>
                <Field label="Category">
                  <SelectMenu<number>
                    value={form.categoryId}
                    onChange={(v) => set('categoryId', v)}
                    disabled={!canEdit}
                    options={[{ value: 0, label: 'Uncategorised' }, ...(categories.data ?? []).map((c) => ({ value: c.id, label: c.name }))]}
                  />
                </Field>
                <p className="text-xs text-muted">Changes here take effect for readers when the article is next published.</p>
              </CardBody>
            </Card>

            {current && <KbAttachmentsCard articleId={current.id} canEdit={canEdit} onUpload={uploadFile} onInsert={appendToBody} />}

            {current && (isManager || perms?.canDelete) && (
              <Card>
                <CardBody className="space-y-2 pt-5">
                  {isManager && (
                    <Button variant="secondary" className="w-full" disabled={busy} loading={archive.isPending}
                      onClick={() => (archived || confirm('Archive this article? Readers and ticket suggestions stop showing it; its history is kept and it can be restored.')) && archive.mutate()}>
                      {archived ? <><ArchiveRestore size={16} /> Unarchive</> : <><Archive size={16} /> Archive</>}
                    </Button>
                  )}
                  {perms?.canDelete && (
                    <Button variant="secondary" className="w-full text-red-600" disabled={busy} loading={remove.isPending}
                      onClick={() => confirm('Delete this draft? It has never been published, so nothing links to it. This can’t be undone.') && remove.mutate()}>
                      <Trash2 size={16} /> Delete draft
                    </Button>
                  )}
                </CardBody>
              </Card>
            )}
          </div>
        </div>
      </Page>

      {modal === 'publish' && (
        <PublishModal
          visibility={form.visibility}
          companies={(companies.data ?? []).filter((c) => form.companyIds.includes(c.id)).map((c) => c.name)}
          firstTime={!ever}
          saving={dirty}
          pending={publish.isPending}
          onClose={() => setModal(null)}
          onConfirm={(note) => publish.mutate(note)}
        />
      )}
      {modal === 'decline' && current && (
        <NoteModal
          title="Decline publish request"
          intro={`${current.publishRequestedBy?.name ?? 'The engineer'} asked for this draft to be published. Declining clears the request; the draft stays as it is.`}
          label="Reason (optional)"
          placeholder="e.g. Needs screenshots of the new settings page"
          confirmLabel="Decline request"
          maxLength={300}
          pending={decline.isPending}
          onClose={() => setModal(null)}
          onConfirm={(note) => decline.mutate(note)}
        />
      )}
    </>
  )
}

function VisibilityOption({ active, disabled, onClick, icon, title, text }: {
  active: boolean; disabled?: boolean; onClick: () => void; icon: React.ReactNode; title: string; text: string
}) {
  return (
    <button type="button" onClick={onClick} disabled={disabled} aria-pressed={active}
      className={cn('flex items-start gap-3 rounded-lg border p-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-60',
        active ? 'border-brand-500 bg-brand-50' : 'border-line hover:bg-gray-50')}>
      <span className={cn('mt-0.5', active ? 'text-brand-600' : 'text-muted')}>{icon}</span>
      <span>
        <span className="block text-sm font-semibold">{title}</span>
        <span className="block text-xs text-muted">{text}</span>
      </span>
    </button>
  )
}

function PublishModal({ visibility, companies, firstTime, saving, pending, onClose, onConfirm }: {
  visibility: KbVisibility; companies: string[]; firstTime: boolean; saving: boolean; pending: boolean
  onClose: () => void; onConfirm: (note: string) => void
}) {
  const audience = visibility === 'PUBLIC'
    ? companies.length ? `staff, and customers of ${companies.join(', ')}` : 'staff and every customer whose plan includes the Help Centre'
    : companies.length ? `staff working with ${companies.join(', ')}` : 'all staff'
  return (
    <NoteModal
      title={firstTime ? 'Publish article' : 'Publish changes'}
      intro={saving
        ? `Your edits will be saved and ${firstTime ? 'this article' : 'the new version'} will be readable straight away by ${audience}.`
        : `${firstTime ? 'This article' : 'The new version'} will be readable straight away by ${audience}.`}
      label="Change note (optional)"
      placeholder={firstTime ? 'First version' : 'e.g. Added the Windows 11 steps'}
      confirmLabel="Publish"
      maxLength={200}
      pending={pending}
      onClose={onClose}
      onConfirm={onConfirm}
    />
  )
}

function NoteModal({ title, intro, label, placeholder, confirmLabel, maxLength, pending, onClose, onConfirm }: {
  title: string; intro: string; label: string; placeholder: string; confirmLabel: string; maxLength: number; pending: boolean
  onClose: () => void; onConfirm: (note: string) => void
}) {
  const [note, setNote] = useState('')
  return (
    <Modal open onClose={onClose} title={title}
      footer={<>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button onClick={() => onConfirm(note)} loading={pending}>{confirmLabel}</Button>
      </>}>
      <p className="mb-4 text-sm text-muted">{intro}</p>
      <Field label={label}>
        <Input value={note} onChange={(e) => setNote(e.target.value)} maxLength={maxLength} placeholder={placeholder} autoFocus
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); onConfirm(note) } }} />
      </Field>
    </Modal>
  )
}
