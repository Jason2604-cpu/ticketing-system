import { api } from '../lib/api'
import type { User } from '../types'

export const authApi = {
  login: (email: string, password: string) => api.post<{ message: string }>('/auth/login', { email, password }),
  logout: () => api.post<{ message: string }>('/auth/logout'),
  me: () => api.get<User>('/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<{ message: string }>('/auth/change-password', { currentPassword, newPassword }),
  activate: (token: string, password: string) => api.post<{ message: string }>('/auth/activate', { token, password }),
  forgotPassword: (email: string) => api.post<{ message: string }>('/auth/forgot-password', { email }),
  resetPassword: (token: string, password: string) => api.post<{ message: string }>('/auth/reset-password', { token, password }),
}
