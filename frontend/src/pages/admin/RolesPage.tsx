import { useState, type FormEvent } from 'react'
import { ROLE_META, ROLE_ORDER } from '../../lib/roleMeta'
import { RoleBadge } from '../../components/ui/RoleBadge'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Lock, Pencil, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Checkbox, Field, Input, Textarea } from '../../components/ui/Input'
import { GenericBadge } from '../../components/ui/Badge'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { rolesApi } from '../../api/roles'
import { errorMessage, isApiError } from '../../lib/api'
import type { Privilege, RoleDef, RoleRequest } from '../../types'

const PRIVILEGES: { value: Privilege; label: string; description: string }[] = [
  { value: 'MANAGE_USERS', label: 'Manage Users', description: 'Create, edit, deactivate and remove users; assign companies and roles' },
  { value: 'MANAGE_COMPANIES', label: 'Manage Companies', description: 'Create and edit companies, their logo and subscription' },
  { value: 'MANAGE_PLANS', label: 'Manage Plans & Billing', description: 'Create and edit subscription plans, features and billing overrides' },
  { value: 'MANAGE_ROLES', label: 'Manage Roles', description: 'Create custom roles, tick their privileges, and grant them to staff' },
  { value: 'MANAGE_CONTRACTS', label: 'Manage Contracts', description: 'Create and edit recurring service contracts, their schedules and renewals' },
  { value: 'MANAGE_TEMPLATES', label: 'Manage Templates', description: 'Create and edit ticket templates and canned replies for the team' },
  { value: 'MANAGE_ASSETS', label: 'Manage Assets', description: 'Register and edit customer assets, their warranties and documents' },
  { value: 'MANAGE_KNOWLEDGE_BASE', label: 'Manage Knowledge Base', description: "Write, publish and archive any article; approve engineers' publish requests; manage categories" },
  { value: 'VIEW_AUDIT_LOG', label: 'View Audit Log', description: 'Read the security and activity trail: sign-ins, permission changes, record edits' },
]
const privilegeLabel = (p: Privilege) => PRIVILEGES.find((o) => o.value === p)?.label ?? p
const empty: RoleRequest = { name: '', description: '', privileges: [] }

/** Admin Panel → Roles: create custom roles, tick which privileges they carry, grant them to staff. */
export default function RolesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState<RoleDef | 'new' | null>(null)

  const roles = useQuery({ queryKey: ['roles'], queryFn: rolesApi.list })
  const invalidate = () => { void qc.invalidateQueries({ queryKey: ['roles'] }); void qc.invalidateQueries({ queryKey: ['users'] }) }
  const remove = useMutation({ mutationFn: (r: RoleDef) => rolesApi.remove(r.id), onSuccess: () => { invalidate(); toast('Role deleted') }, onError: (e) => toast(errorMessage(e), 'error') })

  const system = (roles.data ?? []).filter((r) => !r.custom)
  const custom = (roles.data ?? []).filter((r) => r.custom)

  return (
    <>
      <Topbar title="Role Management" subtitle="Create custom roles and tick which privileges they carry" search={false} newTicket={false} actions={<Button onClick={() => setEditing('new')}><Plus size={16} /> New role</Button>} />
      <Page>
        <div className="mb-3 flex items-center gap-2 text-sm font-medium text-muted"><Lock size={14} /> System roles</div>
        <div className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-5">
          {/* In the shared order, with the same names and icons as the Users page. */}
          {ROLE_ORDER.filter((role) => system.some((r) => r.name === role)).map((role) => (
            <Card key={role}>
              <CardBody className="space-y-2 pt-5 text-sm">
                <RoleBadge role={role} />
                <p className="text-xs text-muted">{ROLE_META[role].description}.</p>
                <p className="text-xs text-muted">Built in — can't be edited or removed.</p>
              </CardBody>
            </Card>
          ))}
        </div>

        <div className="mb-3 flex items-center gap-2 text-sm font-medium text-muted"><ShieldCheck size={14} /> Custom roles</div>
        {custom.length === 0 ? (
          <Card><CardBody><EmptyState title="No custom roles yet" hint="Create a role, tick its privileges, then grant it to a user from the Users page." action={<Button onClick={() => setEditing('new')}><Plus size={16} /> New role</Button>} /></CardBody></Card>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {custom.map((r) => (
              <Card key={r.id} className="flex flex-col">
                <CardHeader title={r.name} action={
                  <div className="flex gap-1">
                    <button className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink" onClick={() => setEditing(r)} aria-label="Edit"><Pencil size={15} /></button>
                    <button className="rounded-md p-1.5 text-muted hover:bg-red-50 hover:text-red-600" onClick={() => confirm(`Delete role "${r.name}"? Anyone holding it loses its privileges immediately.`) && remove.mutate(r)} aria-label="Delete"><Trash2 size={15} /></button>
                  </div>
                } />
                <CardBody className="flex-1 space-y-3 text-sm">
                  {r.description && <p className="text-muted">{r.description}</p>}
                  <div className="flex flex-wrap gap-1.5">
                    {r.privileges.length === 0 ? <span className="text-xs italic text-gray-400">No privileges ticked</span>
                      : r.privileges.map((p) => <GenericBadge key={p} tone="purple">{privilegeLabel(p)}</GenericBadge>)}
                  </div>
                </CardBody>
              </Card>
            ))}
          </div>
        )}
      </Page>
      {editing && <RoleFormModal role={editing} onClose={() => setEditing(null)} onSaved={invalidate} />}
    </>
  )
}

function RoleFormModal({ role, onClose, onSaved }: { role: RoleDef | 'new'; onClose: () => void; onSaved: () => void }) {
  const toast = useToast()
  const isNew = role === 'new'
  const [form, setForm] = useState<RoleRequest>(isNew ? empty : { name: role.name, description: role.description ?? '', privileges: role.privileges })

  const save = useMutation({
    mutationFn: () => (isNew ? rolesApi.create(form) : rolesApi.update(role.id, form)),
    onSuccess: () => { onSaved(); toast(isNew ? 'Role created' : 'Role updated'); onClose() },
  })
  const fields = isApiError(save.error) ? save.error.fields ?? {} : {}
  const submit = (e: FormEvent) => { e.preventDefault(); if (form.name.trim()) save.mutate() }
  const togglePrivilege = (p: Privilege, on: boolean) => setForm((f) => ({ ...f, privileges: on ? [...f.privileges, p] : f.privileges.filter((x) => x !== p) }))

  return (
    <Modal open onClose={onClose} title={isNew ? 'New role' : `Edit ${role.name}`}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="role-form" loading={save.isPending}>{isNew ? 'Create role' : 'Save changes'}</Button></>}>
      <form id="role-form" onSubmit={submit} className="space-y-4">
        {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
        <Field label="Role name" error={fields.name}><Input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. Support Lead" /></Field>
        <Field label="Description" hint="Shown to admins when granting this role — optional"><Textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
        <div>
          <p className="mb-2 text-sm font-medium">Privileges</p>
          <p className="mb-2 text-xs text-muted">Granted to employees on the Users page. Ticked privileges add to what their role allows — a Default employee gets exactly these pages and nothing else. (Admins already hold every privilege.)</p>
          <div className="space-y-2 rounded-lg border border-line p-3">
            {PRIVILEGES.map((p) => (
              <Checkbox key={p.value} label={`${p.label} — ${p.description}`} checked={form.privileges.includes(p.value)} onChange={(e) => togglePrivilege(p.value, e.target.checked)} />
            ))}
          </div>
        </div>
      </form>
    </Modal>
  )
}
