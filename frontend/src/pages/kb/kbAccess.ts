import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../auth/useAuth'
import { kbApi } from '../../api/kb'

export type KbBase = '/kb' | '/help'

/**
 * Who the signed-in user is to the Knowledge Base. Mirrors the backend's KbAccess — the backend
 * decides; this only chooses which buttons and links to show.
 *  - manager: Admin, or anyone holding MANAGE_KNOWLEDGE_BASE — the only ones who publish.
 *  - writer:  managers and Engineers (Engineers press "Request publish" instead of publishing).
 *  - reader:  staff on /kb; customers on /help (their plan decides whether there is anything to read).
 */
export function useKbAccess() {
  const { hasRole, hasPrivilege } = useAuth()
  const isManager = hasRole('ADMIN') || hasPrivilege('MANAGE_KNOWLEDGE_BASE')
  const isWriter = isManager || hasRole('ENGINEER')
  const isCustomer = hasRole('CUSTOMER', 'CUSTOMER_ADMIN')
  const base: KbBase = isCustomer ? '/help' : '/kb'
  return { isManager, isWriter, isCustomer, isStaffReader: isWriter, base }
}

/**
 * "Needs publishing": how many articles are waiting for a manager — the sidebar badge and the tab
 * on Manage articles share this one cached count. Mutations that change it (publish, decline,
 * request) invalidate the key; the bell refreshes it when a publish notification arrives.
 */
export function useKbRequestedCount(enabled: boolean) {
  const q = useQuery({
    queryKey: ['kb', 'manage', 'requested-count'],
    queryFn: kbApi.requestedCount,
    enabled,
    refetchInterval: 60_000,
  })
  return enabled ? q.data?.count ?? 0 : 0
}
