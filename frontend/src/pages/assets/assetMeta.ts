import type { AssetAssignment, AssetStatus, AssetType, WarrantyState } from '../../types'

/** Mirrors AssetType on the backend, in the order the form and filters show them. */
export const ASSET_TYPES: { value: AssetType; label: string }[] = [
  { value: 'SERVER', label: 'Server' },
  { value: 'WORKSTATION', label: 'Workstation' },
  { value: 'LAPTOP', label: 'Laptop' },
  { value: 'NETWORK_DEVICE', label: 'Network device' },
  { value: 'FIREWALL', label: 'Firewall' },
  { value: 'STORAGE', label: 'Storage' },
  { value: 'PRINTER', label: 'Printer' },
  { value: 'UPS', label: 'UPS' },
  { value: 'SOFTWARE_LICENSE', label: 'Software licence' },
  { value: 'PERIPHERAL', label: 'Peripheral' },
  { value: 'OTHER', label: 'Other' },
]

export const ASSET_STATUSES: { value: AssetStatus; label: string; description: string }[] = [
  { value: 'ACTIVE', label: 'Active', description: 'In service' },
  { value: 'IN_REPAIR', label: 'In repair', description: 'With the vendor, on the bench, awaiting a part' },
  { value: 'IN_STORAGE', label: 'In storage', description: 'Working but not deployed — a spare' },
  { value: 'RETIRED', label: 'Retired', description: 'Out of service, kept on the register for history' },
  { value: 'DISPOSED', label: 'Disposed', description: 'Physically gone — scrapped, returned, sold' },
]

export const ASSIGNMENTS: { value: AssetAssignment; label: string; description: string }[] = [
  { value: 'SYSTEM_USER', label: 'A user in the system', description: 'Someone with a login here' },
  { value: 'GUEST', label: 'Someone else', description: 'A named person with no portal account' },
  { value: 'SHARED', label: 'Shared / infrastructure', description: 'Firewalls, switches, printers, servers' },
]

export const WARRANTY_STATES: { value: WarrantyState; label: string }[] = [
  { value: 'IN_WARRANTY', label: 'In warranty' },
  { value: 'EXPIRING_SOON', label: 'Expiring soon' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'NONE', label: 'Not recorded' },
]

export const typeLabel = (t: AssetType) => ASSET_TYPES.find((o) => o.value === t)?.label ?? t
export const statusLabel = (s: AssetStatus) => ASSET_STATUSES.find((o) => o.value === s)?.label ?? s
export const assignmentLabel = (a: AssetAssignment) => ASSIGNMENTS.find((o) => o.value === a)?.label ?? a
export const warrantyLabel = (w: WarrantyState) => WARRANTY_STATES.find((o) => o.value === w)?.label ?? w

/**
 * NONE is grey, not red. "We have no warranty date for this" is a gap in the record; "this went
 * out of warranty in March" is a fact about the asset. Painting them the same colour would send
 * someone chasing a renewal that may not exist.
 */
export const WARRANTY_TONE: Record<WarrantyState, 'green' | 'brand' | 'red' | 'gray'> = {
  IN_WARRANTY: 'green',
  EXPIRING_SOON: 'brand',
  EXPIRED: 'red',
  NONE: 'gray',
}

/** Retired and disposed kit reads quietly — it is on the register for history, not for action. */
export const STATUS_TONE: Record<AssetStatus, 'green' | 'brand' | 'blue' | 'gray'> = {
  ACTIVE: 'green',
  IN_REPAIR: 'brand',
  IN_STORAGE: 'blue',
  RETIRED: 'gray',
  DISPOSED: 'gray',
}

/** "in 34 days" / "12 days ago" / "today", from the server-computed daysToWarrantyEnd. */
export function warrantyPhrase(days: number): string {
  if (days === 0) return 'today'
  if (days > 0) return `in ${days} day${days === 1 ? '' : 's'}`
  const past = Math.abs(days)
  return `${past} day${past === 1 ? '' : 's'} ago`
}
