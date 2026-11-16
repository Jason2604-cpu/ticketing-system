import { useAuth } from '../auth/useAuth'
import type { FeatureCode } from '../types'

/**
 * Whether the current user's company subscription enables a feature.
 *
 * Staff (admins/engineers) aren't gated by a customer's plan, so they always get true — a plan
 * limits what a *customer* has bought, not what the helpdesk team can do.
 *
 * This hides UI only. Every gated endpoint calls requireFeature on the backend as well, so a
 * customer who types the URL still gets a 403 rather than the page.
 */
export function useFeature(code: FeatureCode): boolean {
  const { user, hasRole } = useAuth()
  if (!user) return false
  if (hasRole('ADMIN', 'ENGINEER')) return true
  return !!user.companyFeatures?.includes(code)
}
