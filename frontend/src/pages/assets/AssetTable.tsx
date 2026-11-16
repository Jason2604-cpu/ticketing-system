import { HardDrive, UserX } from 'lucide-react'
import { GenericBadge } from '../../components/ui/Badge'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { formatDateOnly } from '../../lib/format'
import type { Asset } from '../../types'
import { STATUS_TONE, WARRANTY_TONE, warrantyPhrase } from './assetMeta'

/** The asset register table — used by a company's asset list and by an asset group's page. */
export function AssetTable({ rows, onOpen, showGroup = true }: {
  rows: Asset[]
  onOpen: (a: Asset) => void
  /** Off on a group's own page, where every row would say the same thing. */
  showGroup?: boolean
}) {
  return (
    <Table>
      <thead className="bg-gray-50/60">
        <tr>
          <Th>Asset</Th><Th>Type</Th>{showGroup && <Th>Group</Th>}<Th>Serial / location</Th><Th>Used by</Th><Th>Warranty</Th><Th>Status</Th>
        </tr>
      </thead>
      <tbody>
        {rows.map((a) => <AssetRow key={a.id} a={a} showGroup={showGroup} onOpen={() => onOpen(a)} />)}
      </tbody>
    </Table>
  )
}

function AssetRow({ a, onOpen, showGroup }: { a: Asset; onOpen: () => void; showGroup: boolean }) {
  return (
    <Tr onClick={onOpen}>
      <Td>
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
            <HardDrive size={15} />
          </span>
          <div className="min-w-0">
            <p className="truncate font-medium">{a.name}</p>
            <p className="truncate text-xs text-muted">
              {a.assetTag ?? 'No tag'}
              {(a.make || a.model) && <span> · {[a.make, a.model].filter(Boolean).join(' ')}</span>}
            </p>
          </div>
        </div>
      </Td>
      <Td><span className="whitespace-nowrap text-sm">{a.typeLabel}</span></Td>
      {showGroup && <Td><span className="whitespace-nowrap text-sm">{a.groupName ?? <span className="text-gray-400">—</span>}</span></Td>}
      <Td>
        <p className="truncate text-sm">{a.serialNumber ?? <span className="text-gray-400">—</span>}</p>
        {a.location && <p className="truncate text-xs text-muted">{a.location}</p>}
      </Td>
      <Td>
        <span className="flex items-center gap-1.5 whitespace-nowrap text-sm">
          {a.assignedTo === 'SHARED' ? <span className="text-muted">{a.usedBy}</span> : a.usedBy}
          {/* An owner who can no longer sign in is the offboarding signal — "what hardware do we
              need back from this person" — so it's surfaced rather than quietly cleaned up. */}
          {a.ownerInactive && (
            <span title="This user is no longer active" className="text-amber-600"><UserX size={14} /></span>
          )}
        </span>
        {a.assignedTo === 'GUEST' && <p className="text-xs text-muted">Not a portal user</p>}
      </Td>
      <Td>
        <GenericBadge tone={WARRANTY_TONE[a.warrantyState]}>{a.warrantyStateLabel}</GenericBadge>
        {a.warrantyEnd && (
          <p className="mt-0.5 whitespace-nowrap text-xs text-muted">
            {formatDateOnly(a.warrantyEnd)}
            {typeof a.daysToWarrantyEnd === 'number' && ` · ${warrantyPhrase(a.daysToWarrantyEnd)}`}
          </p>
        )}
      </Td>
      <Td><GenericBadge tone={STATUS_TONE[a.status]}>{a.statusLabel}</GenericBadge></Td>
    </Tr>
  )
}
