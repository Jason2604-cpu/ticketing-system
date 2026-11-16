import { api } from '../lib/api'
import type { TemplateKind, TemplateRequest, TicketTemplate } from '../types'

export const templatesApi = {
  // Management — admin / MANAGE_TEMPLATES.
  list: () => api.get<TicketTemplate[]>('/templates'),
  create: (body: TemplateRequest) => api.post<TicketTemplate>('/templates', body),
  update: (id: number, body: TemplateRequest) => api.put<TicketTemplate>(`/templates/${id}`, body),
  remove: (id: number) => api.delete<void>(`/templates/${id}`),

  /**
   * The picker. Open to any signed-in user; the backend decides what a customer may see from
   * their own identity, so companyId here is only a hint for staff.
   */
  usable: (kind: TemplateKind, companyId?: number) => {
    const qs = new URLSearchParams({ kind })
    if (companyId) qs.set('companyId', String(companyId))
    return api.get<TicketTemplate[]>(`/templates/usable?${qs.toString()}`)
  },
  /** Fire-and-forget usage counter — never block the UI on it. */
  markUsed: (id: number) => api.post<void>(`/templates/${id}/used`).catch(() => undefined),
}
