import { api } from '../lib/api'
import type { CreateUserRequest, InviteTeamMemberRequest, NotificationPreferences, Role, UpdateProfileRequest, User, UserStatus } from '../types'

export const usersApi = {
  list: (params?: { role?: Role; status?: UserStatus }) => {
    const q = new URLSearchParams()
    if (params?.role) q.set('role', params.role)
    if (params?.status) q.set('status', params.status)
    const qs = q.toString()
    return api.get<User[]>(`/users${qs ? `?${qs}` : ''}`)
  },
  get: (id: number) => api.get<User>(`/users/${id}`),
  create: (body: CreateUserRequest) => api.post<User>('/users', body),
  setStatus: (id: number, status: UserStatus) => api.patch<User>(`/users/${id}/status?status=${status}`),
  resendInvite: (id: number) => api.post<void>(`/users/${id}/resend-invite`),
  remove: (id: number) => api.delete<void>(`/users/${id}`),
  uploadPhoto: (id: number, file: File) => {
    const fd = new FormData(); fd.append('file', file)
    return api.put<User>(`/users/${id}/photo`, fd)
  },
  setCompanies: (id: number, companyIds: number[]) => api.put<{ id: number; name: string }[]>(`/users/${id}/companies`, companyIds),
  setRoles: (id: number, roles: Role[]) => api.put<User>(`/users/${id}/roles`, roles),
  setCustomRoles: (id: number, roleIds: number[]) => api.put<User>(`/users/${id}/custom-roles`, roleIds),
}

/** Company-scoped team management (CUSTOMER_ADMIN for own company, ADMIN for any). */
export const teamApi = {
  members: (companyId: number) => api.get<User[]>(`/companies/${companyId}/users`),
  invite: (companyId: number, body: InviteTeamMemberRequest) => api.post<User>(`/companies/${companyId}/users`, body),
  resendInvite: (companyId: number, userId: number) => api.post<void>(`/companies/${companyId}/users/${userId}/resend-invite`),
  setStatus: (companyId: number, userId: number, status: UserStatus) => api.patch<User>(`/companies/${companyId}/users/${userId}/status?status=${status}`),
  setAdmin: (companyId: number, userId: number, admin: boolean) => api.patch<User>(`/companies/${companyId}/users/${userId}/admin?admin=${admin}`),
  engineers: (companyId: number) => api.get<User[]>(`/companies/${companyId}/engineers`),
}

export const profileApi = {
  update: (body: UpdateProfileRequest) => api.put<User>('/profile', body),
  updateNotifications: (body: NotificationPreferences) => api.put<User>('/profile/notifications', body),
  uploadPhoto: (file: File) => { const fd = new FormData(); fd.append('file', file); return api.put<User>('/profile/photo', fd) },
  deletePhoto: () => api.delete<User>('/profile/photo'),
}
