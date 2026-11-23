import { api } from '../lib/api'
import type { CreateTicketRequest, HoldRequest, Ticket, TicketPriority, TicketStatus, TicketSummary, User } from '../types'

export interface TicketFilter {
  q?: string
  status?: TicketStatus | ''
  priority?: TicketPriority | ''
  companyId?: number | ''
  assigneeId?: number | ''
  /** Only tickets still waiting for a first public staff reply (the dashboard banner's definition). */
  awaitingResponse?: boolean
}

export const ticketsApi = {
  list: (f: TicketFilter = {}) => {
    const p = new URLSearchParams()
    if (f.q) p.set('q', f.q)
    if (f.status) p.set('status', f.status)
    if (f.priority) p.set('priority', f.priority)
    if (f.companyId) p.set('companyId', String(f.companyId))
    if (f.assigneeId) p.set('assigneeId', String(f.assigneeId))
    if (f.awaitingResponse) p.set('awaitingResponse', 'true')
    const qs = p.toString()
    return api.get<TicketSummary[]>(`/tickets${qs ? `?${qs}` : ''}`)
  },
  get: (id: number) => api.get<Ticket>(`/tickets/${id}`),
  create: (body: CreateTicketRequest) => api.post<Ticket>('/tickets', body),
  setStatus: (id: number, status: TicketStatus) => api.patch<Ticket>(`/tickets/${id}/status?status=${status}`),
  assign: (id: number, assigneeId: number | null) => api.patch<Ticket>(`/tickets/${id}/assignee`, { assigneeId }),
  /** Staff only. null = company-level ticket (no requester). */
  setRequester: (id: number, requesterId: number | null) => api.patch<Ticket>(`/tickets/${id}/requester`, { requesterId }),
  /** Staff only. Pauses the resolution SLA; leave hold through setStatus. */
  putOnHold: (id: number, body: HoldRequest) => api.patch<Ticket>(`/tickets/${id}/hold`, body),
  /** Staff only. The backend re-stamps the SLA due dates from the new priority. */
  setPriority: (id: number, priority: TicketPriority) => api.patch<Ticket>(`/tickets/${id}/priority?priority=${priority}`),
  /** replyToId: the comment being replied to (WhatsApp-style quote); omit for an ordinary comment. */
  addComment: (id: number, body: string, internal = false, replyToId?: number | null) =>
    api.post<Ticket>(`/tickets/${id}/comments`, { body, internal, ...(replyToId ? { replyToId } : {}) }),
  attach: (id: number, file: File) => { const fd = new FormData(); fd.append('file', file); return api.post<Ticket>(`/tickets/${id}/attachments`, fd) },
  removeAttachment: (id: number, mediaId: string) => api.delete<void>(`/tickets/${id}/attachments/${mediaId}`),
  assignees: () => api.get<User[]>('/tickets/assignees'),
  /**
   * Open-ticket counts for the sidebar badges: { "<companyId>": n, ..., total: n }.
   * Replaces downloading the whole ticket list just to count it.
   */
  counts: () => api.get<Record<string, number>>('/tickets/counts'),
}
