import { api } from '../lib/api'
import type { Feature, Plan, PlanRequest, PlanStatus, SubscriptionDefaults } from '../types'

export const plansApi = {
  list: (includeInactive = false) => api.get<Plan[]>(`/admin/plans?includeInactive=${includeInactive}`),
  get: (id: number) => api.get<Plan>(`/admin/plans/${id}`),
  create: (body: PlanRequest) => api.post<Plan>('/admin/plans', body),
  update: (id: number, body: PlanRequest) => api.put<Plan>(`/admin/plans/${id}`, body),
  setStatus: (id: number, status: PlanStatus) => api.patch<Plan>(`/admin/plans/${id}/status?status=${status}`),
  remove: (id: number) => api.delete<void>(`/admin/plans/${id}`),
}

export const featuresApi = {
  list: () => api.get<Feature[]>('/admin/features'),
}

export const subscriptionAdminApi = {
  defaults: () => api.get<SubscriptionDefaults>('/admin/subscriptions/defaults'),
}
