import type { MultiSelectOption } from '../../components/ui/MultiSelectMenu'
import type { Asset, AssetGroup, AssetSummary } from '../../types'

/**
 * One asset as a picker option. The tag goes in the label (it is what people search by); type and
 * serial go in the description line so two identical "Dell Latitude 5440"s can be told apart.
 */
export function assetOption(a: Asset | AssetSummary): MultiSelectOption<number> {
  const retired = 'status' in a && (a.status === 'RETIRED' || a.status === 'DISPOSED')
  return {
    value: a.id,
    label: a.assetTag ? `${a.name} (${a.assetTag})` : a.name,
    description: [a.typeLabel, a.serialNumber ? `SN ${a.serialNumber}` : null, retired ? ('statusLabel' in a ? a.statusLabel : null) : null]
      .filter(Boolean).join(' · ') || undefined,
  }
}

export function groupOption(g: AssetGroup): MultiSelectOption<number> {
  return {
    value: g.id,
    label: g.name,
    description: `${g.assetCount} asset${g.assetCount === 1 ? '' : 's'}${g.description ? ` · ${g.description}` : ''}`,
  }
}
