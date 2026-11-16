import { api } from '../lib/api'
import type { Company, CompanySubscription, CompanySubscriptionRequest, CreateCompanyRequest, SubscriptionStatus, UpdateCompanyRequest } from '../types'

export const companiesApi = {
  list: () => api.get<Company[]>('/companies'),
  get: (id: number) => api.get<Company>(`/companies/${id}`),
  create: (body: CreateCompanyRequest) => api.post<Company>('/companies', body),
  update: (id: number, body: UpdateCompanyRequest) => api.put<Company>(`/companies/${id}`, body),
  subscription: (id: number) => api.get<CompanySubscription>(`/companies/${id}/subscription`),
  assignSubscription: (id: number, body: CompanySubscriptionRequest) =>
    api.put<CompanySubscription>(`/companies/${id}/subscription`, body),
  setSubscriptionStatus: (id: number, status: SubscriptionStatus) =>
    api.patch<CompanySubscription>(`/companies/${id}/subscription/status?status=${status}`),
  hasFeature: (id: number, code: string) => api.get<{ featureCode: string; enabled: boolean }>(`/companies/${id}/features/${code}`),
  uploadLogo: (id: number, file: File) => { const fd = new FormData(); fd.append('file', file); return api.put<Company>(`/companies/${id}/logo`, fd) },
  deleteLogo: (id: number) => api.delete<Company>(`/companies/${id}/logo`),
}
