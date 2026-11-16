import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, HardDrive, UserX } from 'lucide-react'
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
import { AssetFilterBar } from './AssetFilterBar'
import { useAssetFilters } from './assetFilters'
import { STATUS_TONE, WARRANTY_TONE, warrantyPhrase } from './assetMeta'

/**
 * One company's asset register.
 *
 * The company comes from the URL, not a dropdown — that is what makes it impossible to file an
 * asset against the wrong customer. Filters come from the query string, so this page is
 * deep-linkable (the "View assets" button on a contract, phase 7) and pasteable.
 *
 * Filtering runs server-side, one query per change, so the page never shows a filtered view of a
 * truncated list.
 */
export default function AssetsPage() {
  const { companyId: raw } = useParams()
  const companyId = Number(raw)
  const { filters, set, clearAll, active, chips } = useAssetFilters()

  // The company's name comes from the asset-module's own company list rather than the companies
  // API: that endpoint is scoped by exactly the rule this page already obeys, so it needs no
  // second permission surface and no extra round trip for someone who arrived from the picker.
  const companies = useQuery({ queryKey: ['assets', 'companies'], queryFn: assetsApi.companies })
  const company = companies.data?.find((c) => c.companyId === companyId)

  const assets = useQuery({
    queryKey: ['assets', 'list', companyId, filters],
    queryFn: () => assetsApi.list({
      companyId,
      types: filters.types.length ? filters.types : undefined,
      statuses: filters.statuses.length ? filters.statuses : undefined,
      assignedTo: filters.assignedTo || undefined,
      warrantyState: filters.warranty || undefined,
      warrantyEndFrom: filters.from || undefined,
      warrantyEndTo: filters.to || undefined,
      q: filters.q || undefined,
    }),
    enabled: Number.isFinite(companyId) && companyId > 0,
  })

  const rows = useMemo(() => assets.data ?? [], [assets.data])

  if (!Number.isFinite(companyId) || companyId <= 0) {
    return (
      <>
        <Topbar title="Assets" search={false} newTicket={false} />
        <Page><Alert tone="error">That isn't a valid company.</Alert></Page>
      </>
    )
  }

  return (
    <>
      <Topbar
        title={company?.companyName ?? 'Assets'}
        subtitle="Equipment, warranties and coverage"
        search={false}
        newTicket={false}
        actions={
          <Link to="/admin/assets">
            <Button variant="secondary"><ArrowLeft size={16} /> All companies</Button>
          </Link>
        }
      />
      <Page>
        <AssetFilterBar filters={filters} set={set} clearAll={clearAll} active={active} chips={chips} />

        {assets.isError ? (
          <Alert tone="error">{errorMessage(assets.error)}</Alert>
        ) : assets.isLoading ? (
          <Card><div className="p-10 text-center text-sm text-muted">Loading assets…</div></Card>
        ) : rows.length === 0 ? (
          <Card>
            <EmptyState
              icon={<HardDrive size={22} />}
              title={active ? 'No assets match those filters' : 'No assets registered yet'}
              hint={active
                ? 'Try clearing a filter — the chips above show what is currently applied.'
                : "This company's equipment register is empty. Once it's filled in, every ticket and contract can point at the actual device."}
              action={active ? <Button variant="secondary" onClick={clearAll}>Clear filters</Button> : undefined}
            />
          </Card>
        ) : (
          <>
            <p className="mb-2 text-sm text-muted">
              {rows.length} asset{rows.length === 1 ? '' : 's'}{active && ' matching'}
            </p>
            <Card className="overflow-hidden">
              <Table>
                <thead className="bg-gray-50/60">
                  <tr>
                    <Th>Asset</Th><Th>Type</Th><Th>Serial / location</Th><Th>Used by</Th><Th>Warranty</Th><Th>Status</Th>
                  </tr>
                </thead>
                <tbody>
                  {/* Rows aren't clickable yet — the detail page lands in phase 3, and a row that
                      looks clickable but isn't is worse than one that doesn't pretend. */}
                  {rows.map((a) => <AssetRow key={a.id} a={a} />)}
                </tbody>
              </Table>
            </Card>
          </>
        )}
      </Page>
    </>
  )
}

function AssetRow({ a }: { a: Asset }) {
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
              {a.assetTag ?? 'No tag'}
              {(a.make || a.model) && <span> · {[a.make, a.model].filter(Boolean).join(' ')}</span>}
            </p>
          </div>
        </div>
      </Td>
      <Td><span className="whitespace-nowrap text-sm">{a.typeLabel}</span></Td>
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
