import { useCallback, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Boxes, HardDrive, Plus } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { Table, Td, Th, Tr } from '../../components/ui/Table'
import { assetsApi } from '../../api/assets'
import { assetGroupsApi } from '../../api/assetGroups'
import { errorMessage } from '../../lib/api'
import { formatDateOnly } from '../../lib/format'
import { AssetTable } from './AssetTable'
import type { AssetGroup } from '../../types'
import { cn } from '../../lib/cn'
import { AssetGroupFormModal } from './AssetGroupFormModal'
import { AssetFilterBar } from './AssetFilterBar'
import { AssetFormModal } from './AssetFormModal'
import { NO_GROUP, useAssetFilters } from './assetFilters'

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
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  // The tab lives in the URL so "back" from a group's page lands on the groups list again.
  const [params, setParams] = useSearchParams()
  const tab: 'assets' | 'groups' = params.get('tab') === 'groups' ? 'groups' : 'assets'
  const setTab = (t: 'assets' | 'groups') =>
    setParams(t === 'groups' ? new URLSearchParams({ tab: 'groups' }) : new URLSearchParams(), { replace: true })
  const [creatingGroup, setCreatingGroup] = useState(false)

  // The company's name comes from the asset-module's own company list rather than the companies
  // API: that endpoint is scoped by exactly the rule this page already obeys, so it needs no
  // second permission surface and no extra round trip for someone who arrived from the picker.
  const companies = useQuery({ queryKey: ['assets', 'companies'], queryFn: assetsApi.companies })
  const company = companies.data?.find((c) => c.companyId === companyId)

  const groups = useQuery({
    queryKey: ['asset-groups', companyId],
    queryFn: () => assetGroupsApi.list(companyId),
    enabled: Number.isFinite(companyId) && companyId > 0,
  })
  const groupName = useCallback(
    (id: string) => groups.data?.find((g) => String(g.id) === id)?.name ?? `Group #${id}`,
    [groups.data],
  )
  const { filters, set, clearAll, active, chips } = useAssetFilters(groupName)

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
      groupIds: filters.groups.filter((g) => g !== NO_GROUP).map(Number),
      ungrouped: filters.groups.includes(NO_GROUP) || undefined,
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
          <div className="flex gap-2">
            <Link to="/admin/assets">
              <Button variant="secondary"><ArrowLeft size={16} /> All companies</Button>
            </Link>
            {/* No privilege check here: the route guard already limits this page to ADMIN,
                ENGINEER or MANAGE_ASSETS, which is exactly who the API lets write. */}
            {tab === 'assets'
              ? <Button onClick={() => setCreating(true)}><Plus size={16} /> Add asset</Button>
              : <Button onClick={() => setCreatingGroup(true)}><Plus size={16} /> New group</Button>}
          </div>
        }
      />
      <Page>
        <div className="mb-4 flex gap-1 border-b border-line">
          {([
            ['assets', 'Assets', assets.data?.length],
            ['groups', 'Asset groups', groups.data?.length],
          ] as const).map(([key, label, count]) => (
            <button
              key={key}
              type="button"
              onClick={() => setTab(key)}
              className={cn(
                '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
                tab === key ? 'border-brand-600 text-brand-700' : 'border-transparent text-muted hover:text-ink',
              )}
            >
              {label}{typeof count === 'number' && <span className="ml-1.5 text-xs text-muted">{count}</span>}
            </button>
          ))}
        </div>

        {tab === 'groups' ? (
          <AssetGroupsPanel
            groups={groups.data}
            loading={groups.isLoading}
            error={groups.isError ? errorMessage(groups.error) : null}
            onNew={() => setCreatingGroup(true)}
            onOpen={(g) => navigate(`/admin/assets/groups/${g.id}`)}
          />
        ) : (<>
        <AssetFilterBar filters={filters} set={set} clearAll={clearAll} active={active} chips={chips} groups={groups.data} />

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
              action={active
                ? <Button variant="secondary" onClick={clearAll}>Clear filters</Button>
                : <Button onClick={() => setCreating(true)}><Plus size={16} /> Add the first asset</Button>}
            />
          </Card>
        ) : (
          <>
            <p className="mb-2 text-sm text-muted">
              {rows.length} asset{rows.length === 1 ? '' : 's'}{active && ' matching'}
            </p>
            <Card className="overflow-hidden">
              <AssetTable rows={rows} onOpen={(a) => navigate(`/admin/assets/${a.id}`)} />
            </Card>
          </>
        )}
        </>)}
      </Page>

      {creatingGroup && (
        <AssetGroupFormModal
          group={null}
          companyId={companyId}
          companyName={company?.companyName}
          onClose={() => setCreatingGroup(false)}
          onSaved={(saved) => { setCreatingGroup(false); if (saved) navigate(`/admin/assets/groups/${saved.id}`) }}
        />
      )}

      {creating && (
        <AssetFormModal
          asset={null}
          companyId={companyId}
          companyName={company?.companyName}
          onClose={() => setCreating(false)}
          onSaved={(saved) => { setCreating(false); navigate(`/admin/assets/${saved.id}`) }}
        />
      )}
    </>
  )
}

/**
 * The company's asset groups as a table. Clicking a row opens the group's page, which lists its
 * assets. Assets join a group from their own form, so there is nothing to edit per row here.
 */
function AssetGroupsPanel({ groups, loading, error, onNew, onOpen }: {
  groups?: AssetGroup[]
  loading: boolean
  error: string | null
  onNew: () => void
  onOpen: (g: AssetGroup) => void
}) {
  if (error) return <Alert tone="error">{error}</Alert>
  if (loading) return <Card><div className="p-10 text-center text-sm text-muted">Loading groups…</div></Card>
  if (!groups?.length) {
    return (
      <Card>
        <EmptyState
          icon={<Boxes size={22} />}
          title="No asset groups yet"
          hint="Group assets by rack, site or system so a service contract can cover them all in one pick. Assets join a group from their own Add/Edit form."
          action={<Button onClick={onNew}><Plus size={16} /> Create the first group</Button>}
        />
      </Card>
    )
  }
  return (
    <>
      <p className="mb-2 text-sm text-muted">{groups.length} group{groups.length === 1 ? '' : 's'}</p>
      <Card className="overflow-hidden">
        <Table>
          <thead className="bg-gray-50/60">
            <tr><Th>Group</Th><Th>Description</Th><Th className="text-right">Assets</Th><Th>Last updated</Th></tr>
          </thead>
          <tbody>
            {groups.map((g) => (
              <Tr key={g.id} onClick={() => onOpen(g)}>
                <Td>
                  <div className="flex items-center gap-3">
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                      <Boxes size={15} />
                    </span>
                    <span className="font-medium">{g.name}</span>
                  </div>
                </Td>
                <Td><span className="line-clamp-2 text-sm text-muted">{g.description ?? '—'}</span></Td>
                <Td className="text-right"><span className="text-sm tabular-nums">{g.assetCount}</span></Td>
                <Td><span className="whitespace-nowrap text-sm text-muted">{formatDateOnly(g.updatedAt.slice(0, 10))}</span></Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      </Card>
    </>
  )
}
