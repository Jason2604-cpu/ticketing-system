import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Lock } from 'lucide-react'
import { plansApi, featuresApi, subscriptionAdminApi } from '../../api/plans'
import { Field, Input, Checkbox } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Alert } from '../../components/ui/Misc'
import { minutesToHuman } from '../../lib/format'
import type { CompanySubscriptionRequest, Limits, Plan, SlaPriority, SlaRow } from '../../types'

/**
 * The requirement's "Subscription Selection" block:
 *  ( ) Select Existing Plan  → dropdown; features / limits / SLA shown read-only (greyed)
 *  ( ) Custom Plan           → feature toggles + limits + SLA all editable
 */
export function SubscriptionEditor({ value, onChange }: { value: CompanySubscriptionRequest; onChange: (v: CompanySubscriptionRequest) => void }) {
  const plans = useQuery({ queryKey: ['plans'], queryFn: () => plansApi.list(false) })
  const features = useQuery({ queryKey: ['features'], queryFn: featuresApi.list })
  const defaults = useQuery({ queryKey: ['subscription-defaults'], queryFn: subscriptionAdminApi.defaults })

  const isCustom = value.subscriptionType === 'CUSTOM'
  const selectedPlan: Plan | undefined = plans.data?.find((p) => p.id === value.planId)

  // Pre-fill custom values from config defaults the first time Custom is chosen.
  useEffect(() => {
    if (isCustom && !value.limits && defaults.data) {
      onChange({ ...value, limits: defaults.data.customDefaults, sla: defaults.data.customDefaultSla, featureCodes: value.featureCodes ?? [] })
    }
  }, [isCustom, value, defaults.data, onChange])

  const shownLimits: Limits | undefined = isCustom ? value.limits : selectedPlan?.limits
  const shownSla: SlaRow[] = isCustom ? (value.sla ?? []) : (selectedPlan?.sla ?? [])
  const enabledCodes = useMemo(
    () => new Set(isCustom ? (value.featureCodes ?? []) : (selectedPlan?.features.filter((f) => f.enabled).map((f) => f.code) ?? [])),
    [isCustom, value.featureCodes, selectedPlan],
  )

  const setLimit = (k: keyof Limits, n: number) => onChange({ ...value, limits: { ...(value.limits ?? { maxUsers: 1, monthlyTicketLimit: 1, storageLimitMb: 1 }), [k]: n } })
  const setSla = (priority: SlaPriority, k: 'responseTimeMinutes' | 'resolutionTimeMinutes', n: number) =>
    onChange({ ...value, sla: (value.sla ?? []).map((r) => (r.priority === priority ? { ...r, [k]: n } : r)) })
  const toggleFeature = (code: string, on: boolean) => {
    const set = new Set(value.featureCodes ?? [])
    if (on) set.add(code); else set.delete(code)
    onChange({ ...value, featureCodes: [...set] })
  }

  const disabledHint = !isCustom && (
    <span className="inline-flex items-center gap-1 text-xs text-muted"><Lock size={12} /> Inherited from plan</span>
  )

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <label className={`flex cursor-pointer items-start gap-3 rounded-xl border p-4 ${!isCustom ? 'border-brand-500 bg-brand-50/40' : 'border-line'}`}>
          <input type="radio" className="mt-1" checked={!isCustom} onChange={() => onChange({ subscriptionType: 'PREDEFINED', planId: value.planId ?? plans.data?.[0]?.id })} />
          <div className="flex-1">
            <p className="font-medium">Select Existing Plan</p>
            <p className="text-sm text-muted">All configuration is inherited and locked.</p>
            {!isCustom && (
              <SelectMenu<number> className="mt-3" value={value.planId ?? ''} placeholder="Choose a plan…" onChange={(id) => onChange({ ...value, planId: id })}
                options={(plans.data ?? []).map((p) => ({ value: p.id, label: p.planName, description: `${p.limits.maxUsers} users · ${p.limits.monthlyTicketLimit} tickets/mo · ${p.features.filter((f) => f.enabled).length} features` }))} />
            )}
          </div>
        </label>
        <label className={`flex cursor-pointer items-start gap-3 rounded-xl border p-4 ${isCustom ? 'border-brand-500 bg-brand-50/40' : 'border-line'}`}>
          <input type="radio" className="mt-1" checked={isCustom} onChange={() => onChange({ subscriptionType: 'CUSTOM', featureCodes: value.featureCodes ?? [], limits: value.limits ?? defaults.data?.customDefaults, sla: value.sla ?? defaults.data?.customDefaultSla })} />
          <div>
            <p className="font-medium">Custom Plan</p>
            <p className="text-sm text-muted">Manually configure features, limits and SLA for this company.</p>
          </div>
        </label>
      </div>

      {!isCustom && !selectedPlan && <Alert tone="warning">Select a plan to see its configuration.</Alert>}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card>
          <CardHeader title="Features" action={disabledHint} />
          <CardBody className="flex flex-col gap-2.5">
            {features.data?.map((f) => (
              <Checkbox key={f.code} label={f.name} disabled={!isCustom} checked={enabledCodes.has(f.code)} onChange={(e) => toggleFeature(f.code, e.target.checked)} />
            ))}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Limits" action={disabledHint} />
          <CardBody className="space-y-3">
            <Field label="Maximum users"><Input type="number" min={1} max={defaults.data?.caps.maxUsers} disabled={!isCustom} value={shownLimits?.maxUsers ?? ''} onChange={(e) => setLimit('maxUsers', Number(e.target.value))} /></Field>
            <Field label="Tickets per month"><Input type="number" min={1} max={defaults.data?.caps.monthlyTicketLimit} disabled={!isCustom} value={shownLimits?.monthlyTicketLimit ?? ''} onChange={(e) => setLimit('monthlyTicketLimit', Number(e.target.value))} /></Field>
            <Field label="Storage (MB)"><Input type="number" min={1} max={defaults.data?.caps.storageLimitMb} disabled={!isCustom} value={shownLimits?.storageLimitMb ?? ''} onChange={(e) => setLimit('storageLimitMb', Number(e.target.value))} /></Field>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="SLA (minutes)" action={disabledHint} />
          <CardBody className="space-y-3">
            {shownSla.map((row) => (
              <div key={row.priority} className="grid grid-cols-[80px_1fr_1fr] items-end gap-2">
                <span className="pb-2.5 text-sm font-medium">{row.priority.charAt(0) + row.priority.slice(1).toLowerCase()}</span>
                <Field label="Response" hint={minutesToHuman(row.responseTimeMinutes)}><Input type="number" min={1} disabled={!isCustom} value={row.responseTimeMinutes} onChange={(e) => setSla(row.priority, 'responseTimeMinutes', Number(e.target.value))} /></Field>
                <Field label="Resolution" hint={minutesToHuman(row.resolutionTimeMinutes)}><Input type="number" min={1} disabled={!isCustom} value={row.resolutionTimeMinutes} onChange={(e) => setSla(row.priority, 'resolutionTimeMinutes', Number(e.target.value))} /></Field>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>
    </div>
  )
}
