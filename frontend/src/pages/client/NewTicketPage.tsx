import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Field, Input, Textarea } from '../../components/ui/Input'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { priorityStyles } from '../../components/ui/badgeStyles'
import { TemplatePicker } from '../../components/ui/TemplatePicker'
import { FileDropZone } from '../../components/ui/FileDropZone'
import { SuggestedArticles } from '../kb/SuggestedArticles'
import { ticketsApi } from '../../api/tickets'
import { companiesApi } from '../../api/companies'
import { useAuth } from '../../auth/useAuth'
import { storageRemaining } from '../../lib/attachments'
import { cn } from '../../lib/cn'
import { errorMessage } from '../../lib/api'
import type { TicketCategory, TicketPriority, TicketTemplate } from '../../types'

const CATEGORIES: { key: TicketCategory; label: string }[] = [
  { key: 'TECHNICAL', label: 'Technical' }, { key: 'BILLING', label: 'Billing' }, { key: 'ACCOUNT', label: 'Account' }, { key: 'FEATURE', label: 'Feature' },
]
const PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export default function NewTicketPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState<TicketCategory>('TECHNICAL')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')
  const [description, setDescription] = useState('')
  const [steps, setSteps] = useState('')
  const [files, setFiles] = useState<File[]>([])
  // The company's storage left, so a file that would be refused on upload is refused here instead —
  // before the ticket exists. Same query (and cache entry) as Settings → Company Info.
  const { user } = useAuth()
  const companyId = user?.companyId
  const company = useQuery({ queryKey: ['companies', companyId], queryFn: () => companiesApi.get(Number(companyId)), enabled: !!companyId })

  const create = useMutation({
    mutationFn: async () => {
      const t = await ticketsApi.create({ title, description, priority, category, stepsToReproduce: steps || undefined })
      for (const f of files) { try { await ticketsApi.attach(t.id, f) } catch (e) { toast(`${f.name}: ${errorMessage(e)}`, 'error') } }
      return t
    },
    onSuccess: (t) => { void qc.invalidateQueries({ queryKey: ['tickets'] }); toast(`Ticket #${t.id} submitted`); navigate(`/tickets/${t.id}`) },
  })
  /**
   * Fills the form from a template, but only where the user hasn't written anything. Someone who
   * types half a description and then reaches for a template is asking for the parts they haven't
   * filled in, not for their own words to be thrown away.
   */
  const applyTemplate = (t: TicketTemplate) => {
    if (t.titleTemplate && !title.trim()) setTitle(t.titleTemplate)
    if (t.body && !description.trim()) setDescription(t.body)
    if (t.stepsTemplate && !steps.trim()) setSteps(t.stepsTemplate)
    if (t.category) setCategory(t.category)
    if (t.priority) setPriority(t.priority)
  }

  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate() }
  const canSubmit = title.trim().length > 3 && description.trim().length > 10

  return (
    <>
      <Topbar title="Submit a Ticket" search={false} newTicket={false} />
      <Page>
        <Card className="mx-auto max-w-3xl p-8">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold">Submit a Support Ticket</h2>
              <p className="text-sm text-muted">Describe your issue clearly so we can resolve it quickly.</p>
            </div>
            {/*
              Renders nothing when no template applies to this user, so the form is unchanged for
              an installation that hasn't set any up. A template only ever fills EMPTY fields —
              see applyTemplate: picking one after you've started typing must not wipe your work.
            */}
            <TemplatePicker kind="TICKET" onPick={applyTemplate} label="Start from a template" />
          </div>
          <form onSubmit={submit} className="mt-6 space-y-6">
            {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
            <Field label="Issue Title *"><Input required maxLength={200} placeholder="Brief, clear summary of the issue" value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
            {/* Help Centre articles matching the title, if the plan includes it. "This solved it"
                drops the ticket; anything else leaves the form exactly as it was. */}
            <SuggestedArticles title={title} className="-mt-3"
              onSolved={() => { toast('Glad that helped — no ticket was raised'); navigate('/') }} />

            <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
              <div>
                <p className="mb-1.5 text-sm font-medium">Category</p>
                <div className="grid grid-cols-2 gap-2">
                  {CATEGORIES.map((c) => (
                    <button type="button" key={c.key} onClick={() => setCategory(c.key)}
                      className={cn('rounded-lg border px-3 py-2.5 text-sm font-medium', category === c.key ? 'border-brand-600 bg-brand-600 text-white' : 'border-line bg-surface hover:bg-gray-50')}>{c.label}</button>
                  ))}
                </div>
              </div>
              <div>
                <p className="mb-1.5 text-sm font-medium">Priority</p>
                <div className="space-y-2">
                  {PRIORITIES.map((p) => (
                    <button type="button" key={p} onClick={() => setPriority(p)}
                      className={cn('flex w-full items-center gap-2 rounded-lg border px-3 py-2 text-sm', priority === p ? 'border-brand-500 bg-brand-50 font-medium text-brand-700' : 'border-line bg-surface hover:bg-gray-50')}>
                      <i className="h-2 w-2 rounded-full" style={{ background: priorityStyles[p].dot }} /> {priorityStyles[p].label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <Field label="Description *"><Textarea required className="min-h-32" placeholder="What happened? When did it start? Steps to reproduce? Any error messages?" value={description} onChange={(e) => setDescription(e.target.value)} /></Field>
            <Field label="Steps to Reproduce (optional)"><Textarea className="min-h-20" placeholder={'1. Go to…\n2. Click on…\n3. See error'} value={steps} onChange={(e) => setSteps(e.target.value)} /></Field>

            <div>
              <p className="mb-1.5 text-sm font-medium">Attachments <span className="font-normal text-muted">(optional)</span></p>
              <FileDropZone files={files} onChange={setFiles} storageRemainingBytes={storageRemaining(company.data?.subscription)} />
            </div>

            <div className="flex justify-between border-t border-line pt-5">
              <Button type="button" variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
              <Button type="submit" disabled={!canSubmit} loading={create.isPending}><Send size={16} /> Submit Ticket</Button>
            </div>
          </form>
        </Card>
      </Page>
    </>
  )
}
