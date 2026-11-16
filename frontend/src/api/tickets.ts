import { api } from '../lib/api'
import type { CreateTicketRequest, Ticket, TicketPriority, TicketStatus, TicketSummary, User } from '../types'

export interface TicketFilter {
  q?: string
  status?: TicketStatus | ''
  priority?: TicketPriority | ''
  companyId?: number | ''
  assigneeId?: number | ''
}

export const ticketsApi = {
  list: (f: TicketFilter = {}) => {
    const p = new URLSearchParams()
    if (f.q) p.set('q', f.q)
    if (f.status) p.set('status', f.status)
    if (f.priority) p.set('priority', f.priority)
    if (f.companyId) p.set('companyId', String(f.companyId))
    if (f.assigneeId) p.set('assigneeId', String(f.assigneeId))
    const qs = p.toString()
    return api.get<TicketSummary[]>(`/tickets${qs ? `?${qs}` : ''}`)
  },
  get: (id: number) => api.get<Ticket>(`/tickets/${id}`),
  create: (body: CreateTicketRequest) => api.post<Ticket>('/tickets', body),
  setStatus: (id: number, status: TicketStatus) => api.patch<Ticket>(`/tickets/${id}/status?status=${status}`),
  assign: (id: number, assigneeId: number | null) => api.patch<Ticket>(`/tickets/${id}/assignee`, { assigneeId }),
  /** Staff only. The backend re-stamps the SLA due dates from the new priority. */
  setPriority: (id: number, priority: TicketPriority) => api.patch<Ticket>(`/tickets/${id}/priority?priority=${priority}`),
  addComment: (id: number, body: string, internal = false) => api.post<Ticket>(`/tickets/${id}/comments`, { body, internal }),
  attach: (id: number, file: File) => { const fd = new FormData(); fd.append('file', file); return api.post<Ticket>(`/tickets/${id}/attachments`, fd) },
  removeAttachment: (id: number, mediaId: string) => api.delete<void>(`/tickets/${id}/attachments/${mediaId}`),
  assignees: () => api.get<User[]>('/tickets/assignees'),
  /**
   * Open-ticket counts for the sidebar badges: { "<companyId>": n, ..., total: n }.
   * Replaces downloading the whole ticket list just to count it.
   */
  counts: () => api.get<Record<string, number>>('/tickets/counts'),
}
