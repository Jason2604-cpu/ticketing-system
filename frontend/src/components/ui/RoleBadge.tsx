import { GenericBadge } from './Badge'
import { CUSTOM_ROLE_ICON, CUSTOM_ROLE_TONE, ROLE_META } from '../../lib/roleMeta'
import type { CustomRoleRef, Role } from '../../types'

/** A system role (Admin, Engineer, Default, Company Admin, Customer) — same icon and colour everywhere. */
export function RoleBadge({ role }: { role: Role }) {
  const meta = ROLE_META[role]
  const Icon = meta.icon
  return <GenericBadge tone={meta.tone}><Icon size={12} className="mr-1 inline" />{meta.label}</GenericBadge>
}

/** A custom role from Role Management. */
export function CustomRoleBadge({ name }: { name: string }) {
  const Icon = CUSTOM_ROLE_ICON
  return <GenericBadge tone={CUSTOM_ROLE_TONE}><Icon size={11} className="mr-1 inline" />{name}</GenericBadge>
}

/**
 * Everything a user holds, as the user list shows it. One badge for their role, then one per custom
 * role. "Default" is only shown when it is the whole story — an employee with custom roles is
 * described by those. Admin is shown alone: it includes Engineer and every custom privilege.
 */
export function UserRoleBadges({ role, customRoles }: { role: Role; customRoles?: CustomRoleRef[] }) {
  // An Admin already has every privilege, so custom roles would only be noise next to it.
  const custom = role === 'ADMIN' ? [] : customRoles ?? []
  return (
    <div className="flex flex-wrap gap-1.5">
      {!(role === 'STAFF' && custom.length > 0) && <RoleBadge role={role} />}
      {custom.map((r) => <CustomRoleBadge key={r.id} name={r.name} />)}
    </div>
  )
}
