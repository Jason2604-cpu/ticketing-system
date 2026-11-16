import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { Field, Input, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { CATEGORY_OPTS, PRIORITY_OPTS } from './ticketOptions'
import { ticketsApi } from '../../api/tickets'
import { companiesApi } from '../../api/companies'
import { useAuth } from '../../auth/useAuth'
import { errorMessage, isApiError } from '../../lib/api'
import type { TicketCategory, TicketPriority } from '../../types'
import { SuggestedArticles } from '../kb/SuggestedArticles'

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

  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list, enabled: open && isStaff })

  const create = useMutation({
    mutationFn: () => ticketsApi.create({ title, description, priority, category, companyId: isStaff ? Number(companyId) : undefined }),
    onSuccess: (t) => {
      void qc.invalidateQueries({ queryKey: ['tickets'] })
      toast(`Ticket #${t.id} created`)
      onClose(); setTitle(''); setDescription(''); setPriority('MEDIUM')
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
          <Field label="Company" error={fields.companyId}>
            <SelectMenu<number> value={companyId} placeholder="Select company…" onChange={setCompanyId} options={(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))} />
          </Field>
        ) : (
          <Field label="Company"><Input disabled value={user?.companyName ?? ''} /></Field>
        )}
        <Field label="Description" error={fields.description}><Textarea required minLength={10} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What happened, steps to reproduce, impact…" /></Field>
      </form>
    </Modal>
  )
}
