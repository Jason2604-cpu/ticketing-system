import { useEffect, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'
import { Card } from '../../components/ui/Card'
import { Input } from '../../components/ui/Input'
import { Button } from '../../components/ui/Button'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { MultiSelectMenu } from '../../components/ui/MultiSelectMenu'
import type { AssetAssignment, AssetGroup, AssetStatus, AssetType, WarrantyState } from '../../types'
import { ASSET_STATUSES, ASSET_TYPES, ASSIGNMENTS, WARRANTY_STATES } from './assetMeta'
import { NO_GROUP, type AssetFilters, type FilterChip } from './assetFilters'

/**
 * The asset list's filter bar. All state lives in the URL (see useAssetFilters) — this component
 * only renders it.
 *
 * The warranty date inputs are two plain `<input type="date">` rather than the shared
 * DateRangePicker: that control is built around report presets ("last 7 days", "this month"),
 * which are relative to today and point backwards. "Warranty ends between" is an absolute window
 * that usually points forwards, so the presets would be actively wrong here.
 */
export function AssetFilterBar({ filters, set, clearAll, active, chips, groups }: {
  filters: AssetFilters
  /** The company's asset groups, for the group filter. */
  groups?: AssetGroup[]
  set: (patch: Partial<AssetFilters>) => void
  clearAll: () => void
  active: boolean
  chips: FilterChip[]
}) {
  return (
    <Card className="mb-4 p-3">
      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <SearchBox value={filters.q} onChange={(q) => set({ q })} />
          <MultiSelectMenu
            className="lg:w-44"
            values={filters.types}
            onChange={(v) => set({ types: v as AssetType[] })}
            options={ASSET_TYPES}
            emptyLabel="All types"
          />
          <MultiSelectMenu
            className="lg:w-44"
            values={filters.statuses}
            onChange={(v) => set({ statuses: v as AssetStatus[] })}
            options={ASSET_STATUSES.map(({ value, label }) => ({ value, label }))}
            emptyLabel="Any status"
          />
          <MultiSelectMenu
            className="lg:w-48"
            values={filters.groups}
            onChange={(v) => set({ groups: v })}
            options={[
              { value: NO_GROUP, label: 'No group' },
              ...(groups ?? []).map((g) => ({ value: String(g.id), label: g.name })),
            ]}
            emptyLabel="Any group"
            searchPlaceholder="Search groups…"
          />
        </div>

        <div className="flex flex-col gap-3 md:flex-row md:items-center">
          <SelectMenu
            className="md:w-48"
            value={filters.warranty}
            onChange={(v) => set({ warranty: v === 'ALL' ? '' : (v as WarrantyState) })}
            placeholder="Any warranty"
            options={[{ value: 'ALL', label: 'Any warranty' }, ...WARRANTY_STATES]}
          />
          <SelectMenu
            className="md:w-52"
            value={filters.assignedTo}
            onChange={(v) => set({ assignedTo: v === 'ALL' ? '' : (v as AssetAssignment) })}
            placeholder="Anyone"
            options={[{ value: 'ALL', label: 'Assigned to anyone' }, ...ASSIGNMENTS.map(({ value, label }) => ({ value, label }))]}
          />
          <div className="flex items-center gap-2">
            <span className="whitespace-nowrap text-sm text-muted">Warranty ends</span>
            <Input
              type="date"
              aria-label="Warranty ends on or after"
              className="w-40"
              value={filters.from}
              onChange={(e) => set({ from: e.target.value })}
            />
            <span className="text-muted">–</span>
            <Input
              type="date"
              aria-label="Warranty ends on or before"
              className="w-40"
              value={filters.to}
              onChange={(e) => set({ to: e.target.value })}
            />
          </div>
        </div>

        {active && (
          <div className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted">Filtered by</span>
            {chips.map((c) => (
              <button
                key={c.key}
                onClick={c.clear}
                className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700 hover:bg-brand-100"
              >
                {c.label}
                <X size={12} />
              </button>
            ))}
            <Button variant="ghost" size="sm" onClick={clearAll} className="ml-auto">Clear all</Button>
          </div>
        )}
      </div>
    </Card>
  )
}

/**
 * Debounced search, kept in sync with the URL in both directions.
 *
 * `pushed` remembers the last value this box wrote, so a change arriving from the other direction
 * — someone clearing the Search chip — is recognised as external and resets the input, while the
 * value the box itself just pushed doesn't bounce back and fight what is being typed.
 */
function SearchBox({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [text, setText] = useState(value)
  const pushed = useRef(value)

  useEffect(() => {
    if (value !== pushed.current) {
      pushed.current = value
      setText(value)
    }
  }, [value])

  useEffect(() => {
    if (text === value) return
    const t = setTimeout(() => { pushed.current = text; onChange(text) }, 300)
    return () => clearTimeout(t)
  }, [text, value, onChange])

  return (
    <div className="relative flex-1">
      <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
      <Input
        className="pl-9"
        placeholder="Search name, tag, serial, model, location…"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
    </div>
  )
}
