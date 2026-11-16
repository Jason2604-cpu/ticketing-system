import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { AssetAssignment, AssetStatus, AssetType, WarrantyState } from '../../types'
import { assignmentLabel, statusLabel, typeLabel, warrantyLabel } from './assetMeta'
import { formatDateOnlyShort } from '../../lib/format'

export interface AssetFilters {
  q: string
  types: AssetType[]
  statuses: AssetStatus[]
  assignedTo: AssetAssignment | ''
  warranty: WarrantyState | ''
  /** Warranty END date range. A date range is a column and belongs in SQL; warrantyState isn't. */
  from: string
  to: string
}

export interface FilterChip {
  key: string
  label: string
  clear: () => void
}

const csv = (v: string | null): string[] => (v ? v.split(',').filter(Boolean) : [])

/**
 * Asset list filters, held in the URL query string rather than component state.
 *
 * THIS IS A DESIGN CONSTRAINT, NOT A DETAIL. Three things depend on it: the "View assets" deep
 * link from a contract (phase 7) has somewhere to point, the browser back button walks filter
 * changes instead of leaving the page, and a filtered view is something you can paste to a
 * colleague. Retrofitting it later means rewriting the page.
 *
 * Every write uses `replace: true` so typing in the search box doesn't push thirty history
 * entries that the back button then has to walk out of one keystroke at a time.
 */
export function useAssetFilters() {
  const [params, setParams] = useSearchParams()

  const filters = useMemo<AssetFilters>(() => ({
    q: params.get('q') ?? '',
    types: csv(params.get('type')) as AssetType[],
    statuses: csv(params.get('status')) as AssetStatus[],
    assignedTo: (params.get('assignedTo') ?? '') as AssetAssignment | '',
    warranty: (params.get('warranty') ?? '') as WarrantyState | '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
  }), [params])

  const set = useCallback((patch: Partial<AssetFilters>) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      const put = (key: string, value: string) => (value ? next.set(key, value) : next.delete(key))
      if ('q' in patch) put('q', patch.q ?? '')
      if ('types' in patch) put('type', (patch.types ?? []).join(','))
      if ('statuses' in patch) put('status', (patch.statuses ?? []).join(','))
      if ('assignedTo' in patch) put('assignedTo', patch.assignedTo ?? '')
      if ('warranty' in patch) put('warranty', patch.warranty ?? '')
      if ('from' in patch) put('from', patch.from ?? '')
      if ('to' in patch) put('to', patch.to ?? '')
      return next
    }, { replace: true })
  }, [setParams])

  const clearAll = useCallback(() => {
    setParams(new URLSearchParams(), { replace: true })
  }, [setParams])

  const active =
    filters.q.trim() !== '' || filters.types.length > 0 || filters.statuses.length > 0 ||
    filters.assignedTo !== '' || filters.warranty !== '' || filters.from !== '' || filters.to !== ''

  /**
   * One chip per active filter.
   *
   * Without these, someone arriving on a pre-filtered URL reads a subset as the whole register —
   * a contract covering three of a company's forty assets looks like a company with three assets.
   * The chips are what make a filtered view legible to somebody who didn't apply the filter.
   */
  const chips = useMemo<FilterChip[]>(() => {
    const out: FilterChip[] = []
    if (filters.q.trim()) out.push({ key: 'q', label: `Search: "${filters.q.trim()}"`, clear: () => set({ q: '' }) })
    filters.types.forEach((t) => out.push({
      key: `type:${t}`, label: typeLabel(t),
      clear: () => set({ types: filters.types.filter((x) => x !== t) }),
    }))
    filters.statuses.forEach((s) => out.push({
      key: `status:${s}`, label: statusLabel(s),
      clear: () => set({ statuses: filters.statuses.filter((x) => x !== s) }),
    }))
    if (filters.assignedTo) out.push({
      key: 'assignedTo', label: assignmentLabel(filters.assignedTo), clear: () => set({ assignedTo: '' }),
    })
    if (filters.warranty) out.push({
      key: 'warranty', label: `Warranty: ${warrantyLabel(filters.warranty)}`, clear: () => set({ warranty: '' }),
    })
    if (filters.from || filters.to) out.push({
      key: 'range',
      label: filters.from && filters.to
        ? `Warranty ends ${formatDateOnlyShort(filters.from)} – ${formatDateOnlyShort(filters.to)}`
        : filters.from ? `Warranty ends after ${formatDateOnlyShort(filters.from)}`
        : `Warranty ends before ${formatDateOnlyShort(filters.to)}`,
      clear: () => set({ from: '', to: '' }),
    })
    return out
  }, [filters, set])

  return { filters, set, clearAll, active, chips }
}
