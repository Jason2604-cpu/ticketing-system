import { api } from '../lib/api'
import type { RoleDef, RoleRequest } from '../types'

/** Role Management: admin-created custom roles with hand-picked privileges. */
export const rolesApi = {
  list: () => api.get<RoleDef[]>('/roles'),
  create: (body: RoleRequest) => api.post<RoleDef>('/roles', body),
  update: (id: number, body: RoleRequest) => api.put<RoleDef>(`/roles/${id}`, body),
  remove: (id: number) => api.delete<void>(`/roles/${id}`),
}
