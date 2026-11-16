import { api } from '../lib/api'
import type {
  Asset, AssetAssignment, AssetCompanyRow, AssetRequest, AssetStatus, AssetType, WarrantyState,
} from '../types'

export interface AssetListParams {
  companyId?: number
  types?: AssetType[]
  statuses?: AssetStatus[]
  assignedTo?: AssetAssignment
  ownerId?: number
  /** Any of these groups. */
  groupIds?: number[]
  /** Include assets with no group (OR-ed with groupIds). */
  ungrouped?: boolean
  warrantyState?: WarrantyState
  warrantyEndFrom?: string
  warrantyEndTo?: string
  q?: string
}

/**
 * Multi-value filters go out comma-joined (`?type=LAPTOP,SERVER`) rather than as repeated
 * params. Spring binds either into a List, and a comma-joined value is what the page already
 * keeps in its own URL — so the address bar and the request say the same thing, which matters
 * when someone pastes a filtered link to a colleague.
 */
function toQuery(p: AssetListParams): string {
  const qs = new URLSearchParams()
  if (p.companyId) qs.set('companyId', String(p.companyId))
  if (p.types?.length) qs.set('type', p.types.join(','))
  if (p.statuses?.length) qs.set('status', p.statuses.join(','))
  if (p.assignedTo) qs.set('assignedTo', p.assignedTo)
  if (p.ownerId) qs.set('ownerId', String(p.ownerId))
  if (p.groupIds?.length) qs.set('groupId', p.groupIds.join(','))
  if (p.ungrouped) qs.set('ungrouped', 'true')
  if (p.warrantyState) qs.set('warrantyState', p.warrantyState)
  if (p.warrantyEndFrom) qs.set('warrantyEndFrom', p.warrantyEndFrom)
  if (p.warrantyEndTo) qs.set('warrantyEndTo', p.warrantyEndTo)
  if (p.q?.trim()) qs.set('q', p.q.trim())
  const s = qs.toString()
  return s ? `?${s}` : ''
}

/**
 * The customer asset register.
 *
 * Reads are open to anyone who can see the company (customers included, if their plan carries
 * ASSET_MANAGEMENT); writes need ADMIN, ENGINEER or the MANAGE_ASSETS privilege.
 */
export const assetsApi = {
  /** The company picker, with each company's asset and warranty counts. */
  companies: () => api.get<AssetCompanyRow[]>('/assets/companies'),
  list: (params: AssetListParams = {}) => api.get<Asset[]>(`/assets${toQuery(params)}`),
  get: (id: number) => api.get<Asset>(`/assets/${id}`),
  create: (body: AssetRequest) => api.post<Asset>('/assets', body),
  update: (id: number, body: AssetRequest) => api.put<Asset>(`/assets/${id}`, body),
  remove: (id: number) => api.delete<void>(`/assets/${id}`),
}
