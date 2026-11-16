import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { HardDrive, MapPin, UserRound } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { GenericBadge } from '../../components/ui/Badge'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { assetsApi } from '../../api/assets'
import { errorMessage } from '../../lib/api'
import { formatDateOnly } from '../../lib/format'
import type { Asset } from '../../types'
import { AssetFilterBar } from '../assets/AssetFilterBar'
import { useAssetFilters } from '../assets/assetFilters'
import { STATUS_TONE, WARRANTY_TONE, warrantyPhrase } from '../assets/assetMeta'

/**
 * The customer portal's view of their own equipment register — read-only.
 *
 * No companyId is sent: the backend resolves a customer to their own company and checks the
 * ASSET_MANAGEMENT plan feature, so passing one from here would only add a value the client could
 * tamper with for no benefit.
 *
 * Read-only because the register is maintained by the support team. A customer editing their own
 * serial numbers would quietly desynchronise what the engineers are working from.
 */
export default function ClientAssetsPage() {
  const { filters, set, clearAll, active, chips } = useAssetFilters()

  const assets = useQuery({
    queryKey: ['assets', 'mine', filters],
    queryFn: () => assetsApi.list({
      types: filters.types.length ? filters.types : undefined,
      statuses: filters.statuses.length ? filters.statuses : undefined,
      assignedTo: filters.assignedTo || undefined,
      warrantyState: filters.warranty || undefined,
      warrantyEndFrom: filters.from || undefined,
      warrantyEndTo: filters.to || undefined,
      q: filters.q || undefined,
    }),
  })

  const rows = useMemo(() => assets.data ?? [], [assets.data])

  return (
    <>
      <Topbar
        title="Our Equipment"
        subtitle="Devices on record with your support team"
        search={false}
        newTicket={false}
      />
      <Page>
        <AssetFilterBar filters={filters} set={set} clearAll={clearAll} active={active} chips={chips} />

        {assets.isError ? (
          <Alert tone="error">{errorMessage(assets.error)}</Alert>
        ) : assets.isLoading ? (
          <Card><div className="p-10 text-center text-sm text-muted">Loading equipment…</div></Card>
        ) : rows.length === 0 ? (
          <Card>
            <EmptyState
              icon={<HardDrive size={22} />}
              title={active ? 'Nothing matches those filters' : 'No equipment on record yet'}
              hint={active
                ? 'Try clearing a filter — the chips above show what is currently applied.'
                : 'Your support team keeps this register. Ask them to add your devices and they will appear here.'}
              action={active ? <Button variant="secondary" onClick={clearAll}>Clear filters</Button> : undefined}
            />
          </Card>
        ) : (
          <Card className="overflow-hidden">
            <Table>
              <thead className="bg-gray-50/60">
                <tr><Th>Equipment</Th><Th>Type</Th><Th>Location</Th><Th>Used by</Th><Th>Warranty</Th><Th>Status</Th></tr>
              </thead>
              <tbody>{rows.map((a) => <ClientAssetRow key={a.id} a={a} />)}</tbody>
            </Table>
          </Card>
        )}
      </Page>
    </>
  )
}

function ClientAssetRow({ a }: { a: Asset }) {
  return (
    <Tr>
      <Td>
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
            <HardDrive size={15} />
          </span>
          <div className="min-w-0">
            <p className="truncate font-medium">{a.name}</p>
            <p className="truncate text-xs text-muted">
              {[a.make, a.model].filter(Boolean).join(' ') || a.assetTag || 'No model recorded'}
              {a.serialNumber && <span> · {a.serialNumber}</span>}
            </p>
          </div>
        </div>
      </Td>
      <Td><span className="whitespace-nowrap text-sm">{a.typeLabel}</span></Td>
      <Td>
        {a.location
          ? <span className="inline-flex items-center gap-1.5 text-sm"><MapPin size={14} className="text-muted" /> {a.location}</span>
          : <span className="text-gray-400">—</span>}
      </Td>
      <Td>
        <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-sm">
          <UserRound size={14} className="text-muted" />
          {a.assignedTo === 'SHARED' ? <span className="text-muted">{a.usedBy}</span> : a.usedBy}
        </span>
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
