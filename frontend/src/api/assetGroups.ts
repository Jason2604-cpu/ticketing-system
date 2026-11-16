import { api } from '../lib/api'
import type { AssetGroup, AssetGroupRequest } from '../types'

/** Asset groups — read with company scope, written by ADMIN, ENGINEER or MANAGE_ASSETS. */
export const assetGroupsApi = {
  list: (companyId: number) => api.get<AssetGroup[]>(`/asset-groups?companyId=${companyId}`),
  get: (id: number) => api.get<AssetGroup>(`/asset-groups/${id}`),
  create: (body: AssetGroupRequest) => api.post<AssetGroup>('/asset-groups', body),
  update: (id: number, body: AssetGroupRequest) => api.put<AssetGroup>(`/asset-groups/${id}`, body),
  remove: (id: number) => api.delete<void>(`/asset-groups/${id}`),
}
