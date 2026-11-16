import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, Lock, Mail, Zap } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Button } from '../../components/ui/Button'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Spinner } from '../../components/ui/Spinner'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { Checkbox, Field, Textarea } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { Modal } from '../../components/ui/Modal'
import { GenericBadge, type BadgeTone } from '../../components/ui/Badge'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { useToast } from '../../components/ui/useToast'
import { automationApi } from '../../api/automation'
import { companiesApi } from '../../api/companies'
import { errorMessage } from '../../lib/api'
import { timeAgo } from '../../lib/format'
import { useAuth } from '../../auth/useAuth'
import type { AutomationRunStatus, AutomationSettings } from '../../types'

/**
 * Automation settings for one company.
 *
 * Customer Admins see their own company and can switch their emails on and off (unless staff have
 * locked it). Staff pick a company; an Admin can change anything including the lock, an Engineer
 * can look but not touch. The backend enforces all of it — the disabled controls here only explain it.
 */
export default function AutomationPage() {
  const { user, hasRole } = useAuth()
  const isStaff = hasRole('ADMIN', 'ENGINEER')
  const [params, setParams] = useSearchParams()

  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list, enabled: isStaff })

  const picked = Number(params.get('company')) || undefined
  const companyId = isStaff ? (picked ?? companies.data?.[0]?.id) : user?.companyId

  return (
    <>
      <Topbar title="Automation" subtitle="Emails and rules that run on their own" search={false} />
      <Page>
        <div className="mx-auto max-w-3xl space-y-6">
          {isStaff && (
            <Field label="Company">
              <SelectMenu
                value={companyId}
                onChange={(id) => setParams({ company: String(id) })}
                options={(companies.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
                placeholder={companies.isLoading ? 'Loading…' : 'Choose a company'}
              />
            </Field>
          )}
          {companyId ? <CompanyAutomation companyId={companyId} /> : !isStaff && (
            <Card><CardBody><EmptyState icon={<Zap size={22} />} title="No company linked" hint="Your account isn't linked to a company, so there is nothing to automate." /></CardBody></Card>
          )}
        </div>
      </Page>
    </>
  )
}

function CompanyAutomation({ companyId }: { companyId: number }) {
  const { hasRole } = useAuth()
  const settings = useQuery({ queryKey: ['automation', companyId], queryFn: () => automationApi.settings(companyId), refetchInterval: false })
  const runs = useQuery({ queryKey: ['automation', companyId, 'runs'], queryFn: () => automationApi.runs(companyId), refetchInterval: false })

  if (settings.isLoading) return <div className="flex justify-center py-16"><Spinner /></div>
  if (settings.isError || !settings.data) return <Alert>{errorMessage(settings.error)}</Alert>
  const s = settings.data

  return (
    <>
      {!s.featureEnabled && (
        <Alert tone="warning">
          {s.companyName}'s plan doesn't include Automation Rules, so nothing below is sent. Add the feature to the company's subscription to switch it on.
        </Alert>
      )}
      {s.featureEnabled && !s.canEdit && hasRole('CUSTOMER_ADMIN') && (
        <Alert tone="info">Your service provider manages these settings. Contact them if you'd like something changed.</Alert>
      )}
      {s.featureEnabled && !s.canEdit && !hasRole('CUSTOMER_ADMIN') && (
        <Alert tone="info">You can view these settings but only an Admin can change them.</Alert>
      )}

      {/* key: after a save the refetched values replace whatever was typed, so the form never shows stale edits */}
      <MonthlySummaryCard
        key={[s.companyId, s.monthlySummaryEnabled, s.monthlySummaryDay, s.monthlySummaryExtraRecipients.join(','), s.customerCanEdit].join('|')}
        s={s}
      />

      <Card>
        <CardHeader title="Recent activity" />
        <div className="pb-2">
          {runs.isLoading ? <div className="flex justify-center py-8"><Spinner /></div>
            : !runs.data?.length ? <div className="px-6 py-8 text-center text-sm text-muted">Nothing has been sent yet. The first email goes out on the next summary date shown above.</div>
            : (
              <Table>
                <thead><tr><Th>Period</Th><Th>Result</Th><Th>Recipients</Th><Th>When</Th><Th>Details</Th></tr></thead>
                <tbody>
                  {runs.data.map((r) => (
                    <Tr key={r.id}>
                      <Td className="font-medium">{r.period}</Td>
                      <Td><GenericBadge tone={RUN_TONE[r.status]}>{r.status === 'SENT' ? 'Sent' : r.status === 'SKIPPED' ? 'Skipped' : 'Failed'}</GenericBadge></Td>
                      <Td>{r.recipients}</Td>
                      <Td className="text-muted">{timeAgo(r.ranAt)}</Td>
                      <Td className="text-muted">{r.detail}{r.status === 'FAILED' && r.attempts > 1 ? ` (attempt ${r.attempts})` : ''}</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}
        </div>
      </Card>
    </>
  )
}

const RUN_TONE: Record<AutomationRunStatus, BadgeTone> = { SENT: 'green', SKIPPED: 'gray', FAILED: 'red' }

function MonthlySummaryCard({ s }: { s: AutomationSettings }) {
  const toast = useToast()
  const qc = useQueryClient()
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')

  const [enabled, setEnabled] = useState(s.monthlySummaryEnabled)
  const [day, setDay] = useState(s.monthlySummaryDay)
  const [extras, setExtras] = useState(s.monthlySummaryExtraRecipients.join('\n'))
  const [customerCanEdit, setCustomerCanEdit] = useState(s.customerCanEdit)
  const [previewOpen, setPreviewOpen] = useState(false)

  const locked = !s.canEdit
  const extraList = extras.split(/[\n,;]+/).map((x) => x.trim()).filter(Boolean)

  const save = useMutation({
    mutationFn: () => automationApi.update(s.companyId, {
      monthlySummaryEnabled: enabled,
      monthlySummaryDay: day,
      monthlySummaryExtraRecipients: extraList,
      ...(isAdmin ? { customerCanEdit } : {}),
    }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['automation', s.companyId] }); toast('Automation settings saved') },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const preview = useQuery({
    queryKey: ['automation', s.companyId, 'preview'],
    queryFn: () => automationApi.preview(s.companyId),
    enabled: previewOpen,
    refetchInterval: false,
    staleTime: 0,
  })
  const test = useMutation({
    mutationFn: () => automationApi.sendTest(s.companyId),
    onSuccess: () => toast('Test email on its way to your inbox'),
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const dirty = enabled !== s.monthlySummaryEnabled || day !== s.monthlySummaryDay
    || extraList.join(',') !== s.monthlySummaryExtraRecipients.join(',') || (isAdmin && customerCanEdit !== s.customerCanEdit)

  const nextAt = new Date(s.nextSummaryAt)
  const nextLabel = new Intl.DateTimeFormat('en-GB', { dateStyle: 'long', timeStyle: 'short', timeZone: s.zone }).format(nextAt)

  return (
    <Card>
      <CardHeader
        title={<span className="flex items-center gap-2"><Mail size={16} /> Monthly ticket summary</span>}
        action={<Button variant="secondary" size="sm" onClick={() => setPreviewOpen(true)}><Eye size={14} /> Preview</Button>}
      />
      <CardBody className="space-y-5">
        <p className="text-sm text-muted">
          Once a month, each Customer Admin gets an email with how many tickets {s.companyName} raised the month before,
          how many were resolved, what is still open, response and resolution times, and SLA results.
        </p>

        <Checkbox
          label={<span className="font-medium">Send the monthly summary</span>}
          checked={enabled} disabled={locked || !s.featureEnabled}
          onChange={(e) => setEnabled(e.target.checked)}
        />

        {enabled && s.featureEnabled && (
          <p className="rounded-lg bg-gray-50 px-3 py-2 text-sm text-ink">
            Next email: <strong>{nextLabel}</strong> ({s.zone}), covering <strong>{s.nextSummaryPeriod}</strong>.
          </p>
        )}

        <Field label="Send on day of the month" hint="Days 1–28, so every month has it. Sent from 8 AM.">
          <SelectMenu
            value={day} disabled={locked || !s.featureEnabled}
            onChange={(v) => setDay(Number(v))}
            options={Array.from({ length: 28 }, (_, i) => ({ value: i + 1, label: i === 0 ? '1st (default)' : ordinal(i + 1) }))}
          />
        </Field>

        <div>
          <div className="mb-1.5 text-sm font-medium text-ink">Who receives it</div>
          {s.monthlySummaryAdminRecipients.length
            ? <ul className="mb-3 space-y-0.5 text-sm text-muted">{s.monthlySummaryAdminRecipients.map((e) => <li key={e}>{e}</li>)}</ul>
            : <p className="mb-3 text-sm text-amber-700">No active Company Admin has email notifications on, so only the extra addresses below would receive it.</p>}
          <Field label="Extra recipients (optional)" hint="One address per line. Company Admins always receive it; add anyone else who should.">
            <Textarea
              value={extras} disabled={locked || !s.featureEnabled} rows={3}
              placeholder="finance@example.com"
              onChange={(e) => setExtras(e.target.value)}
            />
          </Field>
        </div>

        {isAdmin && (
          <div className="rounded-lg border border-line p-3">
            <Checkbox
              label={<span className="flex items-center gap-1.5 font-medium"><Lock size={13} /> Let this company's admins change these settings</span>}
              checked={customerCanEdit} onChange={(e) => setCustomerCanEdit(e.target.checked)}
            />
            <p className="mt-1 pl-6 text-xs text-muted">Turn off to keep these settings under your control. Company Admins will still receive the email.</p>
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
          <Button variant="secondary" size="sm" loading={test.isPending} disabled={!s.featureEnabled} onClick={() => test.mutate()}>
            Send me a test email
          </Button>
          {!locked && (
            <Button loading={save.isPending} disabled={!dirty || !s.featureEnabled} onClick={() => save.mutate()}>Save changes</Button>
          )}
        </div>
      </CardBody>

      <Modal
        open={previewOpen} onClose={() => setPreviewOpen(false)} size="xl"
        title={`Preview — ${preview.data?.period ?? 'last month'}`}
        footer={<Button variant="secondary" onClick={() => setPreviewOpen(false)}>Close</Button>}
      >
        {preview.isLoading ? <div className="flex justify-center py-12"><Spinner /></div>
          : preview.isError || !preview.data ? <Alert>{errorMessage(preview.error)}</Alert>
          : (
            <div className="space-y-3">
              <div className="text-sm"><span className="text-muted">Subject: </span>{preview.data.subject}</div>
              <div className="text-sm text-muted">
                {preview.data.recipients.length ? `Would be sent to ${preview.data.recipients.join(', ')}` : 'No recipients right now, so nothing would be sent.'}
              </div>
              {/* Sandboxed with no permissions: the email HTML is server-built, but it is still rendered as a foreign document. */}
              <iframe title="Monthly summary preview" sandbox="" srcDoc={preview.data.html} className="h-[560px] w-full rounded-lg border border-line bg-white" />
            </div>
          )}
      </Modal>
    </Card>
  )
}

function ordinal(n: number): string {
  const s = ['th', 'st', 'nd', 'rd']
  const v = n % 100
  return n + (s[(v - 20) % 10] || s[v] || s[0])
}
