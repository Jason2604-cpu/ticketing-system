import { api } from '../lib/api'
import type {
  KbArticle, KbArticleRequest, KbArticleSummary, KbAttachment, KbCategory, KbCategoryRequest, KbFeedbackEvent,
  KbLinkedTicket, KbManageArticle, KbManageSummary, KbPage, KbStatus, KbTicketArticle, KbVersion, KbVisibility,
} from '../types'

const qs = (params: Record<string, string | number | boolean | null | undefined>) => {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '' && v !== false) p.set(k, String(v))
  const s = p.toString()
  return s ? `?${s}` : ''
}

/**
 * Knowledge Base. Readers (staff on /kb, customers on /help) only ever get PUBLISHED versions; what
 * each caller may see is decided by the backend from who they are, never from these parameters.
 */
export const kbApi = {
  // ── Reading ───────────────────────────────────────────────────────────────
  /** Recently updated first (sort 'popular': most viewed first), or — with q — a full-text search, best match first, with excerpts. */
  articles: (p: { q?: string; categoryId?: number | null; sort?: 'recent' | 'popular'; page?: number; size?: number } = {}) =>
    api.get<KbPage<KbArticleSummary>>(`/kb/articles${qs(p)}`),
  article: (slug: string) => api.get<KbArticle>(`/kb/articles/${encodeURIComponent(slug)}`),
  categories: () => api.get<KbCategory[]>('/kb/categories'),
  /** Up to five articles for what has been typed so far. companyId is honoured for staff only. */
  suggest: (q: string, companyId?: number | null) => api.get<KbArticleSummary[]>(`/kb/suggest${qs({ q, companyId })}`),
  /** Audit only: a suggestion was opened, or "this solved it" on the ticket form. Never blocks the UI. */
  feedback: (slug: string, event: KbFeedbackEvent) =>
    api.post<void>(`/kb/articles/${encodeURIComponent(slug)}/feedback`, { event }).catch(() => undefined),

  // ── Writing (Admin, Engineer, MANAGE_KNOWLEDGE_BASE) ──────────────────────
  manageList: (p: { status?: KbStatus; visibility?: KbVisibility; categoryId?: number | null; requested?: boolean; q?: string; page?: number; size?: number } = {}) =>
    api.get<KbPage<KbManageSummary>>(`/kb/manage/articles${qs(p)}`),
  /** "Needs publishing": working copies waiting for an Admin / Knowledge Base manager. */
  requestedCount: () => api.get<{ count: number }>('/kb/manage/requested-count'),
  manageGet: (id: number) => api.get<KbManageArticle>(`/kb/manage/articles/${id}`),
  create: (body: KbArticleRequest) => api.post<KbManageArticle>('/kb/manage/articles', body),
  update: (id: number, body: KbArticleRequest) => api.put<KbManageArticle>(`/kb/manage/articles/${id}`, body),
  publish: (id: number, changeNote?: string) => api.post<KbManageArticle>(`/kb/manage/articles/${id}/publish`, { changeNote }),
  requestPublish: (id: number) => api.post<KbManageArticle>(`/kb/manage/articles/${id}/request-publish`),
  declinePublish: (id: number, note?: string) => api.post<KbManageArticle>(`/kb/manage/articles/${id}/decline-publish`, { note }),
  archive: (id: number) => api.post<KbManageArticle>(`/kb/manage/articles/${id}/archive`),
  unarchive: (id: number) => api.post<KbManageArticle>(`/kb/manage/articles/${id}/unarchive`),
  remove: (id: number) => api.delete<void>(`/kb/manage/articles/${id}`),
  versions: (id: number) => api.get<KbVersion[]>(`/kb/manage/articles/${id}/versions`),
  version: (id: number, n: number) => api.get<KbVersion>(`/kb/manage/articles/${id}/versions/${n}`),
  restore: (id: number, n: number) => api.post<KbManageArticle>(`/kb/manage/articles/${id}/versions/${n}/restore`),

  // ── Images and files ──────────────────────────────────────────────────────
  attachments: (id: number) => api.get<KbAttachment[]>(`/kb/manage/articles/${id}/attachments`),
  upload: (id: number, file: File) => { const fd = new FormData(); fd.append('file', file); return api.post<KbAttachment>(`/kb/manage/articles/${id}/attachments`, fd) },
  removeAttachment: (id: number, mediaId: string) => api.delete<void>(`/kb/manage/articles/${id}/attachments/${mediaId}`),

  /** Tickets this article is linked to (the ones the caller can open). */
  linkedTickets: (id: number) => api.get<KbLinkedTicket[]>(`/kb/manage/articles/${id}/tickets`),

  // ── Categories (list: writers; changes: Admin / MANAGE_KNOWLEDGE_BASE) ────
  manageCategories: () => api.get<KbCategory[]>('/kb/manage/categories'),
  createCategory: (body: KbCategoryRequest) => api.post<KbCategory>('/kb/manage/categories', body),
  updateCategory: (id: number, body: KbCategoryRequest) => api.put<KbCategory>(`/kb/manage/categories/${id}`, body),
  removeCategory: (id: number) => api.delete<void>(`/kb/manage/categories/${id}`),
}

/** Articles linked to a ticket. Customers get only the ones they can open; staff link and unlink. */
export const ticketArticlesApi = {
  list: (ticketId: number) => api.get<KbTicketArticle[]>(`/tickets/${ticketId}/articles`),
  link: (ticketId: number, articleId: number) => api.post<KbTicketArticle[]>(`/tickets/${ticketId}/articles/${articleId}`),
  unlink: (ticketId: number, articleId: number) => api.delete<KbTicketArticle[]>(`/tickets/${ticketId}/articles/${articleId}`),
}
