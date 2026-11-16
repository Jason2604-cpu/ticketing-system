import { api } from '../lib/api'
import type { AutomationRun, AutomationSettings, MonthlySummaryPreview, UpdateAutomationSettingsRequest } from '../types'

export const automationApi = {
  settings: (companyId: number) => api.get<AutomationSettings>(`/automation/companies/${companyId}/settings`),
  update: (companyId: number, body: UpdateAutomationSettingsRequest) =>
    api.put<AutomationSettings>(`/automation/companies/${companyId}/settings`, body),
  runs: (companyId: number) => api.get<AutomationRun[]>(`/automation/companies/${companyId}/runs`),
  preview: (companyId: number) => api.get<MonthlySummaryPreview>(`/automation/companies/${companyId}/monthly-summary/preview`),
  sendTest: (companyId: number) => api.post<{ queued: boolean }>(`/automation/companies/${companyId}/monthly-summary/send-test`),
}
