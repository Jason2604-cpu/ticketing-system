import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Field, Input, Select, Textarea } from '../../components/ui/Input'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { isValidEmail } from '../../lib/countries'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { SubscriptionEditor } from './SubscriptionEditor'
import { companiesApi } from '../../api/companies'
import { usersApi } from '../../api/users'
import { errorMessage, isApiError } from '../../lib/api'
import type { CompanySubscriptionRequest } from '../../types'

export default function CompanyFormPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [contactEmail, setContactEmail] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [mainEngineerId, setMainEngineerId] = useState('')
  const [subscription, setSubscription] = useState<CompanySubscriptionRequest>({ subscriptionType: 'PREDEFINED' })

  const staff = useQuery({ queryKey: ['users', 'all'], queryFn: () => usersApi.list() })
  const engineerOptions = (staff.data ?? []).filter((u) => u.role === 'ENGINEER' || u.role === 'ADMIN')

  const create = useMutation({
    mutationFn: () => companiesApi.create({ name, address: address || undefined, contactEmail: contactEmail || undefined, contactPhone: contactPhone || undefined, subscription, mainEngineerId: mainEngineerId ? Number(mainEngineerId) : undefined }),
    onSuccess: (c) => { void qc.invalidateQueries({ queryKey: ['companies'] }); toast(`${c.name} created`); navigate(`/companies/${c.id}`) },
  })
  const fields = isApiError(create.error) ? create.error.fields ?? {} : {}

  const [touched, setTouched] = useState(false)
  const emailOk = !contactEmail || isValidEmail(contactEmail)
  const submit = (e: FormEvent) => { e.preventDefault(); setTouched(true); if (emailOk) create.mutate() }

  return (
    <>
      <Topbar title="New Company" subtitle="Company information and subscription selection" />
      <Page>
        <form onSubmit={submit} className="space-y-4" noValidate>
          {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
          <Card>
            <CardHeader title="Company Information" />
            <CardBody className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field label="Company name" error={fields.name}><Input required maxLength={150} value={name} onChange={(e) => setName(e.target.value)} /></Field>
              <Field label="Contact email" error={fields.contactEmail ?? (touched && !emailOk ? 'Enter a valid email address' : undefined)}><Input type="email" value={contactEmail} invalid={touched && !emailOk} onBlur={() => setTouched(true)} onChange={(e) => setContactEmail(e.target.value)} placeholder="hello@company.com" /></Field>
              <Field label="Contact phone" error={fields.contactPhone}><PhoneInput value={contactPhone} onChange={setContactPhone} /></Field>
              <Field label="Address" className="md:col-span-2"><Textarea value={address} onChange={(e) => setAddress(e.target.value)} className="min-h-16" /></Field>
              <Field label="Main engineer" hint="Notified whenever a new ticket is created for this company">
                <Select value={mainEngineerId} onChange={(e) => setMainEngineerId(e.target.value)}>
                  <option value="">None</option>
                  {engineerOptions.map((u) => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
                </Select>
              </Field>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Subscription Selection" />
            <CardBody><SubscriptionEditor value={subscription} onChange={setSubscription} /></CardBody>
          </Card>

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
            <Button type="submit" loading={create.isPending}>Create company</Button>
          </div>
        </form>
      </Page>
    </>
  )
}
