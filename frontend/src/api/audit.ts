import { api } from '../lib/api'
import type { AuditLevel, AuditPage } from '../types'

export interface AuditFilters {
  action?: string
  actorId?: number
  level?: AuditLevel
  from?: string
  to?: string
  q?: string
}

const toQuery = (f: AuditFilters, extra?: Record<string, string>) => {
  const qs = new URLSearchParams()
  if (f.action) qs.set('action', f.action)
  if (f.actorId) qs.set('actorId', String(f.actorId))
  if (f.level) qs.set('level', f.level)
  if (f.from) qs.set('from', f.from)
  if (f.to) qs.set('to', f.to)
  if (f.q?.trim()) qs.set('q', f.q.trim())
  for (const [k, v] of Object.entries(extra ?? {})) qs.set(k, v)
  return qs.toString()
}

/** The audit trail — admin, or a custom role carrying VIEW_AUDIT_LOG. Read-only by design. */
export const auditApi = {
  list: (filters: AuditFilters, page: number, size: number) =>
    api.get<AuditPage>(`/audit?${toQuery(filters, { page: String(page), size: String(size) })}`),
  /** Absolute URL so the browser downloads it directly, cookie attached. */
  exportUrl: (filters: AuditFilters) => api.absoluteUrl(`/audit/export.csv?${toQuery(filters)}`),
}
