import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { Field, Input, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { HOLD_REASON_OPTS } from './ticketOptions'
import { ticketsApi } from '../../api/tickets'
import { errorMessage, isApiError } from '../../lib/api'
import type { HoldReason } from '../../types'

/** "yyyy-MM-ddTHH:mm" in local time: what a datetime-local input reads, and what the backend's LocalDateTime parses. */
function localInputValue(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * Staff put a ticket On Hold. The customer sees the reason; the note is saved as an internal
 * comment. The resolution SLA is paused until the status is changed back (or, for "waiting for
 * customer reply", until the customer replies).
 */
export function HoldModal({ ticketId, open, onClose }: { ticketId: number; open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [reason, setReason] = useState<HoldReason | ''>('')
  const [note, setNote] = useState('')
  const [until, setUntil] = useState('')

  const hold = useMutation({
    mutationFn: () => ticketsApi.putOnHold(ticketId, { reason: reason as HoldReason, note: note.trim(), until: until || undefined }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tickets'] })
      toast('Ticket on hold — resolution SLA paused')
      setReason(''); setNote(''); setUntil('')
      onClose()
    },
  })
  const fields = isApiError(hold.error) ? hold.error.fields ?? {} : {}
  const canSubmit = !!reason && note.trim().length > 0
  const submit = (e: FormEvent) => { e.preventDefault(); if (canSubmit) hold.mutate() }

  return (
    <Modal open={open} onClose={onClose} title="Put ticket on hold"
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="hold-ticket" disabled={!canSubmit} loading={hold.isPending}>Put on hold</Button></>}>
      <form id="hold-ticket" onSubmit={submit} className="space-y-4">
        <p className="text-sm text-muted">The resolution SLA is paused while the ticket is on hold, and its deadline moves out by the paused time when work resumes. The first-response target still applies.</p>
        {hold.isError && <Alert>{errorMessage(hold.error)}</Alert>}
        <Field label="Reason" hint={reason === 'CUSTOMER_REPLY' ? 'The customer sees this. The ticket resumes by itself as soon as they reply.' : 'The customer sees this.'} error={fields.reason}>
          <SelectMenu<HoldReason> value={reason} placeholder="Select a reason…" onChange={setReason} options={[...HOLD_REASON_OPTS]} />
        </Field>
        <Field label="Note" hint="Internal — the customer will not see this." error={fields.note}>
          <Textarea required maxLength={5000} value={note} onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Replacement PSU ordered from the supplier, ETA Friday" className="border-yellow-300 bg-yellow-50/40" />
        </Field>
        <Field label="Until (optional)" hint="Shown on the ticket, e.g. the booked visit. The ticket doesn't resume by itself at this time — change the status when work restarts." error={fields.until}>
          <Input type="datetime-local" value={until} min={localInputValue(new Date())} onChange={(e) => setUntil(e.target.value)} />
        </Field>
      </form>
    </Modal>
  )
}
