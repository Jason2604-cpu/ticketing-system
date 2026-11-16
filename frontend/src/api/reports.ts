import { api } from '../lib/api'
import type { Report, ReportRange } from '../types'

const qs = (range: ReportRange, companyId?: number) => {
  const p = new URLSearchParams({ from: range.from, to: range.to })
  if (companyId !== undefined) p.set('companyId', String(companyId))
  return p.toString()
}

const BASE = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(/\/$/, '')

export const reportsApi = {
  summary: (range: ReportRange, companyId?: number) => api.get<Report>(`/reports?${qs(range, companyId)}`),

  /**
   * Downloads the CSV. Goes through fetch rather than a plain link so the auth cookie and the
   * backend's error shape behave the same as every other call — an <a href> would render a raw
   * 403 JSON body in a new tab instead of surfacing a toast.
   */
  downloadCsv: async (range: ReportRange, companyId?: number) => {
    const res = await fetch(`${BASE}/reports/export.csv?${qs(range, companyId)}`, { credentials: 'include' })
    if (!res.ok) throw new Error('Export failed')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `ticket-report_${range.from}_to_${range.to}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  },
}
