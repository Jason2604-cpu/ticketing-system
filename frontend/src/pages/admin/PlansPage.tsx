import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Layers, Pencil, Plus, Power, Trash2, X } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Checkbox, Field, Input, Textarea } from '../../components/ui/Input'
import { PlanStatusBadge } from '../../components/ui/Badge'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { plansApi, featuresApi, subscriptionAdminApi } from '../../api/plans'
import { errorMessage } from '../../lib/api'
import { minutesToHuman } from '../../lib/format'
import type { Plan, PlanRequest, SlaPriority } from '../../types'

const empty = (sla: PlanRequest['sla']): PlanRequest => ({ planName: '', description: '', status: 'ACTIVE', featureCodes: [], limits: { maxUsers: 10, monthlyTicketLimit: 100, storageLimitMb: 1024 }, sla })

export default function PlansPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState<Plan | 'new' | null>(null)

  const plans = useQuery({ queryKey: ['plans', 'all'], queryFn: () => plansApi.list(true) })
  const features = useQuery({ queryKey: ['features'], queryFn: featuresApi.list })
  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['plans'] }) }
  const toggle = useMutation({ mutationFn: (p: Plan) => plansApi.setStatus(p.id, p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'), onSuccess: invalidate, onError: (e) => toast(errorMessage(e), 'error') })
  const remove = useMutation({ mutationFn: (p: Plan) => plansApi.remove(p.id), onSuccess: () => { invalidate(); toast('Plan deleted') }, onError: (e) => toast(errorMessage(e), 'error') })

  return (
    <>
      <Topbar title="Subscription Plans" subtitle="Plans, feature catalog, limits and SLA targets" search={false} newTicket={false} actions={<Button variant="secondary" onClick={() => setEditing('new')}><Plus size={16} /> New plan</Button>} />
      <Page>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {plans.data?.map((p) => (
            <Card key={p.id} className="flex flex-col">
              <CardHeader title={<span className="flex items-center gap-2">{p.planName} <PlanStatusBadge status={p.status} /></span>} action={
                <div className="flex gap-1">
                  <button className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" onClick={() => setEditing(p)} aria-label="Edit"><Pencil size={15} /></button>
                  <button className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" onClick={() => toggle.mutate(p)} aria-label="Toggle"><Power size={15} /></button>
                  <button className="rounded-md p-1.5 text-muted hover:bg-red-50 hover:text-red-600" onClick={() => confirm(`Delete plan "${p.planName}"?`) && remove.mutate(p)} aria-label="Delete"><Trash2 size={15} /></button>
                </div>
              } />
              <CardBody className="flex-1 space-y-4 text-sm">
                {p.description && <p className="text-muted">{p.description}</p>}
                <div className="grid grid-cols-3 gap-2 rounded-lg bg-gray-50 p-3 text-center">
                  <div><p className="text-lg font-semibold">{p.limits.maxUsers}</p><p className="text-xs text-muted">users</p></div>
                  <div><p className="text-lg font-semibold">{p.limits.monthlyTicketLimit}</p><p className="text-xs text-muted">tickets/mo</p></div>
                  <div><p className="text-lg font-semibold">{p.limits.storageLimitMb >= 1024 ? `${Math.round(p.limits.storageLimitMb / 1024)} GB` : `${p.limits.storageLimitMb} MB`}</p><p className="text-xs text-muted">storage</p></div>
                </div>
                <ul className="space-y-1.5">
                  {p.features.map((f) => (
                    <li key={f.code} className={`flex items-center gap-2 ${f.enabled ? '' : 'text-gray-400'}`}>
                      {f.enabled ? <Check size={14} className="text-green-600" /> : <X size={14} />} {f.name}
                    </li>
                  ))}
                </ul>
                <div className="border-t border-line pt-3">
                  {p.sla.map((s) => (
                    <div key={s.priority} className="flex justify-between text-xs text-muted"><span>{s.priority.charAt(0) + s.priority.slice(1).toLowerCase()}</span><span>respond {minutesToHuman(s.responseTimeMinutes)} · resolve {minutesToHuman(s.resolutionTimeMinutes)}</span></div>
                  ))}
                </div>
              </CardBody>
            </Card>
          ))}
        </div>
        {plans.data?.length === 0 && <EmptyState icon={<Layers size={22} />} title="No plans" hint="Plans are seeded from app-config.properties, or create one here." />}
      </Page>
      {editing && <PlanModal plan={editing === 'new' ? null : editing} features={features.data?.map((f) => ({ code: f.code, name: f.name })) ?? []} onClose={() => setEditing(null)} />}
    </>
  )
}

function PlanModal({ plan, features, onClose }: { plan: Plan | null; features: { code: string; name: string }[]; onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const defaults = useQuery({ queryKey: ['subscription-defaults'], queryFn: subscriptionAdminApi.defaults })
  const [form, setForm] = useState<PlanRequest | null>(plan ? { planName: plan.planName, description: plan.description ?? '', status: plan.status, featureCodes: plan.features.filter((f) => f.enabled).map((f) => f.code), limits: plan.limits, sla: plan.sla } : null)
  const current = form ?? (defaults.data ? empty(defaults.data.customDefaultSla) : null)

  const save = useMutation({
    mutationFn: (body: PlanRequest) => (plan ? plansApi.update(plan.id, body) : plansApi.create(body)),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['plans'] }); toast(plan ? 'Plan updated' : 'Plan created'); onClose() },
  })
  if (!current) return null
  const set = (patch: Partial<PlanRequest>) => setForm({ ...current, ...patch })
  const setSla = (priority: SlaPriority, k: 'responseTimeMinutes' | 'resolutionTimeMinutes', n: number) => set({ sla: current.sla.map((r) => (r.priority === priority ? { ...r, [k]: n } : r)) })
  const submit = (e: FormEvent) => { e.preventDefault(); save.mutate(current) }

  return (
    <Modal open onClose={onClose} title={plan ? `Edit ${plan.planName}` : 'New plan'} size="lg" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="plan-form" loading={save.isPending}>{plan ? 'Save changes' : 'Create plan'}</Button></>}>
      <form id="plan-form" onSubmit={submit} className="space-y-4">
        {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Plan name"><Input required maxLength={100} value={current.planName} onChange={(e) => set({ planName: e.target.value })} /></Field>
          <Field label="Status"><Checkbox className="mt-3" label="Active (selectable for new companies)" checked={current.status === 'ACTIVE'} onChange={(e) => set({ status: e.target.checked ? 'ACTIVE' : 'INACTIVE' })} /></Field>
        </div>
        <Field label="Description"><Textarea className="min-h-16" value={current.description} onChange={(e) => set({ description: e.target.value })} /></Field>
        <div>
          <p className="mb-2 text-sm font-medium">Features</p>
          <div className="grid grid-cols-2 gap-2">
            {features.map((f) => (
              <Checkbox key={f.code} label={f.name} checked={current.featureCodes.includes(f.code)} onChange={(e) => set({ featureCodes: e.target.checked ? [...current.featureCodes, f.code] : current.featureCodes.filter((c) => c !== f.code) })} />
            ))}
          </div>
        </div>
        <div className="grid grid-cols-3 gap-4">
          <Field label="Max users"><Input type="number" min={1} value={current.limits.maxUsers} onChange={(e) => set({ limits: { ...current.limits, maxUsers: Number(e.target.value) } })} /></Field>
          <Field label="Tickets / month"><Input type="number" min={1} value={current.limits.monthlyTicketLimit} onChange={(e) => set({ limits: { ...current.limits, monthlyTicketLimit: Number(e.target.value) } })} /></Field>
          <Field label="Storage (MB)"><Input type="number" min={1} value={current.limits.storageLimitMb} onChange={(e) => set({ limits: { ...current.limits, storageLimitMb: Number(e.target.value) } })} /></Field>
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">SLA targets (minutes)</p>
          <div className="space-y-2">
            {current.sla.map((row) => (
              <div key={row.priority} className="grid grid-cols-[90px_1fr_1fr] items-center gap-3">
                <span className="text-sm">{row.priority.charAt(0) + row.priority.slice(1).toLowerCase()}</span>
                <Input type="number" min={1} value={row.responseTimeMinutes} onChange={(e) => setSla(row.priority, 'responseTimeMinutes', Number(e.target.value))} placeholder="Response" />
                <Input type="number" min={1} value={row.resolutionTimeMinutes} onChange={(e) => setSla(row.priority, 'resolutionTimeMinutes', Number(e.target.value))} placeholder="Resolution" />
              </div>
            ))}
          </div>
        </div>
      </form>
    </Modal>
  )
}
