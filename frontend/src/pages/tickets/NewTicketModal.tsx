import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { Field, Input, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { Alert } from '../../components/ui/Misc'
import { FileDropZone } from '../../components/ui/FileDropZone'
import { useToast } from '../../components/ui/useToast'
import { CATEGORY_OPTS, PRIORITY_OPTS } from './ticketOptions'
import { ticketsApi } from '../../api/tickets'
import { companiesApi } from '../../api/companies'
import { teamApi } from '../../api/users'
import { useAuth } from '../../auth/useAuth'
import { errorMessage, isApiError } from '../../lib/api'
import { storageRemaining } from '../../lib/attachments'
import type { TicketCategory, TicketPriority } from '../../types'
import { SuggestedArticles } from '../kb/SuggestedArticles'

/** Nobody picked: a company-level ticket, whose updates go to the company's admins. */
const NO_REQUESTER = 0

/** Staff quick-create (customers use the full /tickets/new page). */
export function NewTicketModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user, hasRole } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const navigate = useNavigate()
  const isStaff = hasRole('ADMIN', 'ENGINEER')

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')
  const [category, setCategory] = useState<TicketCategory>('TECHNICAL')
  const [companyId, setCompanyId] = useState<number | ''>(user?.companyId ?? '')
  const [requesterId, setRequesterId] = useState<number>(NO_REQUESTER)
  const [files, setFiles] = useState<File[]>([])

  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list, enabled: open && isStaff })
  // The chosen company's people (for Requester) and its plan (for the storage it has left).
  const members = useQuery({ queryKey: ['team', companyId], queryFn: () => teamApi.members(Number(companyId)), enabled: open && isStaff && !!companyId })
  const company = useQuery({ queryKey: ['companies', companyId], queryFn: () => companiesApi.get(Number(companyId)), enabled: open && !!companyId })

  // Active customer accounts only — the backend refuses anyone else.
  const requesterOpts = [
    { value: NO_REQUESTER, label: '— None (company-level) —', description: "The company's admins get the updates" },
    ...(members.data ?? [])
      .filter((u) => u.status === 'ACTIVE' && (u.role === 'CUSTOMER' || u.role === 'CUSTOMER_ADMIN'))
      .map((u) => ({ value: u.id, label: `${u.firstName} ${u.lastName}`, description: u.email })),
  ]
  // A requester belongs to one company, so a new company means picking again.
  const pickCompany = (id: number) => { setCompanyId(id); setRequesterId(NO_REQUESTER) }
  const reset = () => { setTitle(''); setDescription(''); setPriority('MEDIUM'); setRequesterId(NO_REQUESTER); setFiles([]) }

  const create = useMutation({
    mutationFn: async () => {
      const t = await ticketsApi.create({
        title, description, priority, category,
        companyId: isStaff ? Number(companyId) : undefined,
        requesterId: isStaff && requesterId !== NO_REQUESTER ? requesterId : undefined,
      })
      // Same as the Submit Ticket page: a file the server still refuses doesn't undo the ticket.
      for (const f of files) { try { await ticketsApi.attach(t.id, f) } catch (e) { toast(`${f.name}: ${errorMessage(e)}`, 'error') } }
      return t
    },
    onSuccess: (t) => {
      void qc.invalidateQueries({ queryKey: ['tickets'] })
      if (files.length > 0) void qc.invalidateQueries({ queryKey: ['companies', t.companyId] }) // storage used changed
      toast(`Ticket #${t.id} created`)
      onClose(); reset()
      navigate(`/tickets/${t.id}`)
    },
  })
  const fields = isApiError(create.error) ? create.error.fields ?? {} : {}
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate() }

  return (
    <Modal open={open} onClose={onClose} title="New Ticket" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="new-ticket" loading={create.isPending}>Create ticket</Button></>}>
      <form id="new-ticket" onSubmit={submit} className="space-y-4">
        {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
        <Field label="Title" error={fields.title}><Input required minLength={4} maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Short summary of the issue" /></Field>
        {isStaff && <SuggestedArticles staff title={title} companyId={companyId || null} />}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Category"><SelectMenu value={category} onChange={setCategory} options={[...CATEGORY_OPTS]} /></Field>
          <Field label="Priority"><SelectMenu value={priority} onChange={setPriority} options={PRIORITY_OPTS} /></Field>
        </div>
        {isStaff ? (
          <>
            <Field label="Company" error={fields.companyId}>
              <SelectMenu<number> value={companyId} placeholder="Select company…" onChange={pickCompany} options={(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))} />
            </Field>
            <Field label="Requester (optional)" error={fields.requesterId}
              hint={companyId ? 'Who the ticket is for — they get its emails and notifications.' : 'Pick a company first.'}>
              <SelectMenu<number> value={requesterId} onChange={setRequesterId} options={requesterOpts} disabled={!companyId} />
            </Field>
          </>
        ) : (
          <Field label="Company"><Input disabled value={user?.companyName ?? ''} /></Field>
        )}
        <Field label="Description" error={fields.description}><Textarea required minLength={10} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What happened, steps to reproduce, impact…" /></Field>
        {/* Not inside a Field: that is a <label>, which would turn clicks on the file list into "browse". */}
        <div>
          <p className="mb-1.5 text-sm font-medium">Attachments <span className="font-normal text-muted">(optional)</span></p>
          <FileDropZone files={files} onChange={setFiles} storageRemainingBytes={storageRemaining(company.data?.subscription)} />
        </div>
      </form>
    </Modal>
  )
}
