import { api } from '../lib/api'
import type { ContractRequest, ContractState, ServiceContract, VisitRequest } from '../types'

/** Service contracts — admin, or a custom role carrying MANAGE_CONTRACTS. */
export const contractsApi = {
  list: (params?: { companyId?: number; state?: ContractState; q?: string }) => {
    const qs = new URLSearchParams()
    if (params?.companyId) qs.set('companyId', String(params.companyId))
    if (params?.state) qs.set('state', params.state)
    if (params?.q) qs.set('q', params.q)
    const suffix = qs.toString()
    return api.get<ServiceContract[]>(`/contracts${suffix ? `?${suffix}` : ''}`)
  },
  get: (id: number) => api.get<ServiceContract>(`/contracts/${id}`),
  create: (body: ContractRequest) => api.post<ServiceContract>('/contracts', body),
  update: (id: number, body: ContractRequest) => api.put<ServiceContract>(`/contracts/${id}`, body),
  remove: (id: number) => api.delete<void>(`/contracts/${id}`),
  regenerate: (id: number) => api.post<ServiceContract>(`/contracts/${id}/schedule`),
  updateVisit: (id: number, visitId: number, body: VisitRequest) =>
    api.put<ServiceContract>(`/contracts/${id}/visits/${visitId}`, body),
  addDocument: (id: number, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<ServiceContract>(`/contracts/${id}/documents`, fd)
  },
  removeDocument: (id: number, mediaId: string) => api.delete<ServiceContract>(`/contracts/${id}/documents/${mediaId}`),
}
