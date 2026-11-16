import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Field, Input } from '../../components/ui/Input'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { teamApi } from '../../api/users'
import { cn } from '../../lib/cn'
import type { AssetAssignment } from '../../types'
import { ASSIGNMENTS } from './assetMeta'

export interface AssignmentValue {
  assignedTo: AssetAssignment
  ownerId: number | null
  guestName: string
  guestEmail: string
}

/**
 * Who an asset belongs to — one control, three mutually exclusive modes.
 *
 * A laptop belongs to a person, a firewall belongs to nobody in particular, and a printer belongs
 * to a floor. A single "owner" dropdown left blank can't distinguish the second from "we haven't
 * filled this in", so the mode is always stated and only the relevant input is shown. The backend
 * clears whatever the chosen mode doesn't use, so switching modes twice can't leave a stale owner
 * or a guest named after somebody who now has a login.
 */
export function AssetAssignmentField({ companyId, value, onChange, error }: {
  companyId: number
  value: AssignmentValue
  onChange: (v: AssignmentValue) => void
  error?: string
}) {
  const members = useQuery({
    queryKey: ['team', companyId],
    queryFn: () => teamApi.members(companyId),
    enabled: companyId > 0,
  })

  /**
   * Deactivated accounts are kept OUT of the list — you don't hand a laptop to someone who can no
   * longer sign in — except the one already selected, which stays so that editing an asset owned
   * by a leaver doesn't silently reassign it just because the form couldn't render the name.
   */
  const options = useMemo(() => {
    const all = members.data ?? []
    const selectable = all.filter((u) => u.status === 'ACTIVE' || u.status === 'INVITED')
    const selected = value.ownerId ? all.find((u) => u.id === value.ownerId) : undefined
    const list = selected && !selectable.some((u) => u.id === selected.id) ? [...selectable, selected] : selectable
    return list.map((u) => ({
      value: u.id,
      label: `${u.firstName} ${u.lastName}`.trim() || u.email,
      description: u.status === 'ACTIVE' ? u.email : `${u.email} · ${u.status === 'INVITED' ? 'invited' : 'no longer active'}`,
    }))
  }, [members.data, value.ownerId])

  const setMode = (mode: AssetAssignment) => onChange({ ...value, assignedTo: mode })

  return (
    <div>
      <span className="mb-1.5 block text-sm font-medium text-ink">Assigned to</span>
      <div className="space-y-2">
        {ASSIGNMENTS.map((opt) => {
          const active = value.assignedTo === opt.value
          return (
            <div key={opt.value}>
              <button
                type="button"
                onClick={() => setMode(opt.value)}
                className={cn(
                  'flex w-full items-start gap-3 rounded-lg border p-3 text-left transition-colors',
                  active ? 'border-brand-500 bg-brand-50' : 'border-line hover:bg-gray-50',
                )}
              >
                <span className={cn(
                  'mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border',
                  active ? 'border-brand-600' : 'border-gray-300',
                )}>
                  {active && <span className="h-2 w-2 rounded-full bg-brand-600" />}
                </span>
                <span className="min-w-0">
                  <span className={cn('block text-sm font-medium', active ? 'text-brand-700' : 'text-ink')}>{opt.label}</span>
                  <span className="block text-xs text-muted">{opt.description}</span>
                </span>
              </button>

              {active && opt.value === 'SYSTEM_USER' && (
                <div className="mt-2 pl-7">
                  <SelectMenu
                    value={value.ownerId ?? ''}
                    onChange={(v) => onChange({ ...value, ownerId: v as number })}
                    options={options}
                    placeholder={members.isLoading ? 'Loading people…' : 'Pick a person'}
                    invalid={!!error}
                  />
                  {!members.isLoading && options.length === 0 && (
                    <p className="mt-1 text-xs text-muted">
                      Nobody at this company has an account yet — use “Someone else” and type their name.
                    </p>
                  )}
                </div>
              )}

              {active && opt.value === 'GUEST' && (
                <div className="mt-2 grid grid-cols-1 gap-3 pl-7 sm:grid-cols-2">
                  <Field label="Name">
                    <Input
                      value={value.guestName}
                      onChange={(e) => onChange({ ...value, guestName: e.target.value })}
                      placeholder="Aina Rahman"
                      invalid={!!error}
                    />
                  </Field>
                  {/* Contact detail only. It never becomes a login and the warranty sweep never
                      mails it — it's unverified text somebody typed into a form. */}
                  <Field label="Email (optional)" hint="For reference only — not a login">
                    <Input
                      type="email"
                      value={value.guestEmail}
                      onChange={(e) => onChange({ ...value, guestEmail: e.target.value })}
                      placeholder="aina@example.com"
                    />
                  </Field>
                </div>
              )}
            </div>
          )
        })}
      </div>
      {error && <p className="mt-1.5 text-xs text-red-600">{error}</p>}
    </div>
  )
}
