import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff, FileText, MessageSquareQuote, Pencil, Plus, Trash2 } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Checkbox, Field, Input, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { MultiSelectMenu } from '../../components/ui/MultiSelectMenu'
import { GenericBadge } from '../../components/ui/Badge'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { templatesApi } from '../../api/templates'
import { companiesApi } from '../../api/companies'
import { errorMessage } from '../../lib/api'
import type { TemplateKind, TemplateRequest, TicketCategory, TicketPriority, TicketTemplate } from '../../types'

const CATEGORIES: { value: TicketCategory; label: string }[] = [
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'BILLING', label: 'Billing' },
  { value: 'ACCOUNT', label: 'Account' },
  { value: 'FEATURE', label: 'Feature' },
]
const PRIORITIES: { value: TicketPriority; label: string }[] = [
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
]

const blank = (kind: TemplateKind): TemplateRequest => ({
  kind,
  name: '',
  description: '',
  titleTemplate: '',
  body: '',
  stepsTemplate: '',
  category: null,
  priority: null,
  companyIds: [],
  visibleToCustomers: kind === 'TICKET',
  active: true,
  sortOrder: 0,
})

/**
 * Admin Panel → Templates. Two kinds on one page, because they're the same object with a
 * different destination: TICKET templates prefill the new-ticket form, REPLY templates drop
 * canned text into an engineer's comment box.
 */
export default function TemplatesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [tab, setTab] = useState<TemplateKind>('TICKET')
  const [editing, setEditing] = useState<TicketTemplate | 'new' | null>(null)

  const templates = useQuery({ queryKey: ['templates'], queryFn: templatesApi.list })
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['templates'] })
    void qc.invalidateQueries({ queryKey: ['templates', 'usable'] })
  }

  const remove = useMutation({
    mutationFn: (t: TicketTemplate) => templatesApi.remove(t.id),
    onSuccess: () => { invalidate(); toast('Template deleted') },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const rows = useMemo(() => (templates.data ?? []).filter((t) => t.kind === tab), [templates.data, tab])
  const counts = useMemo(() => ({
    TICKET: (templates.data ?? []).filter((t) => t.kind === 'TICKET').length,
    REPLY: (templates.data ?? []).filter((t) => t.kind === 'REPLY').length,
  }), [templates.data])

  return (
    <>
      <Topbar
        title="Templates"
        subtitle="Ticket templates and canned replies"
        search={false}
        newTicket={false}
        actions={<Button onClick={() => setEditing('new')}><Plus size={16} /> New template</Button>}
      />
      <Page>
        <div className="mb-5 inline-flex rounded-lg border border-line bg-surface p-1">
          <TabButton active={tab === 'TICKET'} onClick={() => setTab('TICKET')} icon={<FileText size={15} />} label="Ticket templates" count={counts.TICKET} />
          <TabButton active={tab === 'REPLY'} onClick={() => setTab('REPLY')} icon={<MessageSquareQuote size={15} />} label="Canned replies" count={counts.REPLY} />
        </div>

        <p className="mb-4 max-w-3xl text-sm text-muted">
          {tab === 'TICKET'
            ? 'Offered on the new-ticket form. A template that asks the right questions up front is the difference between "it’s broken" and a ticket an engineer can act on. Tick "show to customers" to put it on the client portal.'
            : 'Offered in the reply box on a ticket. Staff only — customers never see these.'}
        </p>

        {templates.isLoading ? (
          <Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card>
        ) : rows.length === 0 ? (
          <Card>
            <EmptyState
              icon={tab === 'TICKET' ? <FileText size={22} /> : <MessageSquareQuote size={22} />}
              title={tab === 'TICKET' ? 'No ticket templates yet' : 'No canned replies yet'}
              hint={tab === 'TICKET'
                ? 'Start with the three issues you get most often — the ones where you always end up asking the same follow-up questions.'
                : 'Start with the replies you type most: acknowledging a ticket, asking for logs, confirming a fix.'}
              action={<Button onClick={() => setEditing('new')}><Plus size={16} /> New template</Button>}
            />
          </Card>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {rows.map((t) => (
              <Card key={t.id} className={`flex flex-col ${t.active ? '' : 'opacity-60'}`}>
                <CardBody className="flex flex-1 flex-col gap-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate font-semibold">{t.name}</p>
                      {t.description && <p className="mt-0.5 line-clamp-2 text-xs text-muted">{t.description}</p>}
                    </div>
                    <div className="flex shrink-0 gap-1">
                      <button className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" aria-label="Edit" onClick={() => setEditing(t)}><Pencil size={15} /></button>
                      <button
                        className="rounded-md p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
                        aria-label="Delete"
                        onClick={() => confirm(`Delete "${t.name}"? Tickets already raised from it are unaffected.`) && remove.mutate(t)}
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>

                  <p className="line-clamp-3 flex-1 whitespace-pre-wrap rounded-lg bg-gray-50 p-2.5 font-mono text-xs text-gray-700">{t.body}</p>

                  <div className="flex flex-wrap items-center gap-1.5">
                    {t.companies.length === 0
                      ? <GenericBadge tone="gray">All companies</GenericBadge>
                      : t.companies.slice(0, 2).map((c) => <GenericBadge key={c.id} tone="gray">{c.name}</GenericBadge>)}
                    {t.companies.length > 2 && <GenericBadge tone="gray">+{t.companies.length - 2} more</GenericBadge>}
                    {t.kind === 'TICKET' && t.category && <GenericBadge tone="blue">{t.category}</GenericBadge>}
                    {t.kind === 'TICKET' && t.priority && <GenericBadge tone="purple">{t.priority}</GenericBadge>}
                    {t.kind === 'TICKET' && (
                      <GenericBadge tone={t.visibleToCustomers ? 'green' : 'gray'}>
                        {t.visibleToCustomers ? <><Eye size={11} className="mr-1 inline" />Customers</> : <><EyeOff size={11} className="mr-1 inline" />Staff only</>}
                      </GenericBadge>
                    )}
                    {!t.active && <GenericBadge tone="red">Inactive</GenericBadge>}
                  </div>

                  <p className="text-xs text-muted">Used {t.useCount} time{t.useCount === 1 ? '' : 's'}</p>
                </CardBody>
              </Card>
            ))}
          </div>
        )}
      </Page>

      {editing && (
        <TemplateFormModal
          template={editing === 'new' ? null : editing}
          defaultKind={tab}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); invalidate() }}
        />
      )}
    </>
  )
}

function TabButton({ active, onClick, icon, label, count }: {
  active: boolean; onClick: () => void; icon: React.ReactNode; label: string; count: number
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${active ? 'bg-brand-600 text-white' : 'text-gray-700 hover:bg-gray-50'}`}
    >
      {icon} {label}
      <span className={`rounded-full px-1.5 text-xs ${active ? 'bg-surface/20' : 'bg-gray-100 text-gray-600'}`}>{count}</span>
    </button>
  )
}

function TemplateFormModal({ template, defaultKind, onClose, onSaved }: {
  template: TicketTemplate | null
  defaultKind: TemplateKind
  onClose: () => void
  onSaved: () => void
}) {
  const toast = useToast()
  const editing = template !== null
  const [form, setForm] = useState<TemplateRequest>(template
    ? {
        kind: template.kind,
        name: template.name,
        description: template.description ?? '',
        titleTemplate: template.titleTemplate ?? '',
        body: template.body,
        stepsTemplate: template.stepsTemplate ?? '',
        category: template.category ?? null,
        priority: template.priority ?? null,
        companyIds: template.companies.map((c) => c.id),
        visibleToCustomers: template.visibleToCustomers,
        active: template.active,
        sortOrder: template.sortOrder,
      }
    : blank(defaultKind))
  const [error, setError] = useState<string | null>(null)

  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list })
  const set = <K extends keyof TemplateRequest>(k: K, v: TemplateRequest[K]) => setForm((f) => ({ ...f, [k]: v }))
  const isTicket = form.kind === 'TICKET'

  const save = useMutation({
    mutationFn: (body: TemplateRequest) => (editing ? templatesApi.update(template.id, body) : templatesApi.create(body)),
    onSuccess: () => { toast(editing ? 'Template updated' : 'Template created'); onSaved() },
    onError: (e) => setError(errorMessage(e)),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!form.name.trim()) { setError('Give the template a name'); return }
    if (!form.body.trim()) { setError('A template needs a body'); return }
    save.mutate(form)
  }

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={editing ? `Edit "${template.name}"` : 'New template'}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="template-form" loading={save.isPending}>{editing ? 'Save changes' : 'Create template'}</Button>
        </div>
      }
    >
      <form id="template-form" onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}

        <Field label="Type">
          <SelectMenu
            value={form.kind}
            onChange={(v) => set('kind', v as TemplateKind)}
            options={[
              { value: 'TICKET', label: 'Ticket template', description: 'Prefills the new-ticket form' },
              { value: 'REPLY', label: 'Canned reply', description: 'Inserted into the reply box on a ticket. Staff only.' },
            ]}
          />
        </Field>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Name" hint="What it's called in the picker">
            <Input value={form.name} onChange={(e) => set('name', e.target.value)} placeholder={isTicket ? 'Server unreachable' : 'Acknowledge and ask for logs'} />
          </Field>
          <Field label="Available to" hint="Pick any number of customers, or leave it empty for all of them">
            <MultiSelectMenu
              values={form.companyIds ?? []}
              onChange={(v) => set('companyIds', v)}
              emptyLabel="All companies"
              options={(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
              searchPlaceholder="Search companies…"
            />
          </Field>
        </div>

        <Field label="One-line description (optional)" hint="Shown under the name so people pick the right one">
          <Input value={form.description ?? ''} onChange={(e) => set('description', e.target.value)} maxLength={250} />
        </Field>

        {isTicket && (
          <Field label="Prefilled title (optional)">
            <Input value={form.titleTemplate ?? ''} onChange={(e) => set('titleTemplate', e.target.value)} maxLength={200} placeholder="Server unreachable — [hostname]" />
          </Field>
        )}

        <Field
          label={isTicket ? 'Prefilled description' : 'Reply text'}
          hint={isTicket ? 'Ask for what you always end up asking for. Square brackets read as blanks to fill in.' : undefined}
        >
          <Textarea
            rows={7}
            value={form.body}
            onChange={(e) => set('body', e.target.value)}
            placeholder={isTicket
              ? 'Affected system: [hostname / IP]\nWhen it started: \nWhat changed recently: \nError message shown: \nHow many users affected: '
              : "Thanks for getting in touch — we've picked this up and are looking into it now.\n\nCould you send us the log file from [location]? That'll let us narrow it down quickly."}
          />
        </Field>

        {isTicket && (
          <>
            <Field label="Prefilled steps to reproduce (optional)">
              <Textarea rows={3} value={form.stepsTemplate ?? ''} onChange={(e) => set('stepsTemplate', e.target.value)} placeholder={'1. \n2. \n3. '} />
            </Field>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Prefilled category (optional)">
                <SelectMenu
                  value={form.category ?? ''}
                  onChange={(v) => set('category', v === 'NONE' ? null : (v as TicketCategory))}
                  placeholder="Let the user choose"
                  options={[{ value: 'NONE', label: 'Let the user choose' }, ...CATEGORIES]}
                />
              </Field>
              <Field label="Prefilled priority (optional)">
                <SelectMenu
                  value={form.priority ?? ''}
                  onChange={(v) => set('priority', v === 'NONE' ? null : (v as TicketPriority))}
                  placeholder="Let the user choose"
                  options={[{ value: 'NONE', label: 'Let the user choose' }, ...PRIORITIES]}
                />
              </Field>
            </div>
          </>
        )}

        <div className="space-y-2 border-t border-line pt-4">
          {isTicket && (
            <Checkbox
              className="items-start"
              checked={form.visibleToCustomers}
              onChange={(e) => set('visibleToCustomers', e.target.checked)}
              label={
                <span className="text-sm">
                  Show to customers on the new-ticket form
                  <span className="block text-xs text-muted">Off means staff only — for templates you use when raising a ticket on a customer's behalf.</span>
                </span>
              }
            />
          )}
          <Checkbox
            className="items-start"
            checked={form.active}
            onChange={(e) => set('active', e.target.checked)}
            label={
              <span className="text-sm">
                Active
                <span className="block text-xs text-muted">Unticking hides it from every picker but keeps it here, so you can retire one without losing the wording.</span>
              </span>
            }
          />
        </div>
      </form>
    </Modal>
  )
}
