import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Boxes, Pencil } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { assetGroupsApi } from '../../api/assetGroups'
import { assetsApi } from '../../api/assets'
import { errorMessage } from '../../lib/api'
import { AssetGroupFormModal } from './AssetGroupFormModal'
import { AssetTable } from './AssetTable'

/**
 * One asset group: its details and the assets currently in it. View-only for membership —
 * an asset joins or leaves a group from its own Add/Edit form.
 */
export default function AssetGroupDetailPage() {
  const { groupId: raw } = useParams()
  const groupId = Number(raw)
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)

  const group = useQuery({
    queryKey: ['asset-groups', 'detail', groupId],
    queryFn: () => assetGroupsApi.get(groupId),
    enabled: Number.isFinite(groupId) && groupId > 0,
  })
  const g = group.data

  // Full asset rows (warranty, status, used-by) via the normal list endpoint, filtered to the group.
  const assets = useQuery({
    queryKey: ['assets', 'list', g?.companyId, { groupId }],
    queryFn: () => assetsApi.list({ companyId: g!.companyId, groupIds: [groupId] }),
    enabled: !!g,
  })

  if (group.isLoading) {
    return <><Topbar title="Asset group" search={false} newTicket={false} /><Page><Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card></Page></>
  }
  if (!g) {
    return <><Topbar title="Asset group" search={false} newTicket={false} /><Page><Alert>{group.error ? errorMessage(group.error) : "Couldn't load this group. It may have been deleted."}</Alert></Page></>
  }

  const backTo = `/admin/assets/company/${g.companyId}?tab=groups`
  const rows = assets.data ?? []

  return (
    <>
      <Topbar
        title={g.name}
        subtitle={`${g.companyName} · Asset group`}
        search={false}
        newTicket={false}
        actions={<Button variant="secondary" onClick={() => setEditing(true)}><Pencil size={15} /> Edit group</Button>}
      />
      <Page>
        <Link to={backTo} className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:underline">
          <ArrowLeft size={15} /> {g.companyName} asset groups
        </Link>

        {g.description && <p className="mb-4 max-w-3xl text-sm text-muted">{g.description}</p>}

        {assets.isError ? (
          <Alert tone="error">{errorMessage(assets.error)}</Alert>
        ) : assets.isLoading ? (
          <Card><div className="p-10 text-center text-sm text-muted">Loading assets…</div></Card>
        ) : rows.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Boxes size={22} />}
              title="No assets in this group yet"
              hint="To add one, open an asset (or add a new one) and choose this group in its Asset group field."
              action={<Link to={`/admin/assets/company/${g.companyId}`}><Button variant="secondary">Go to assets</Button></Link>}
            />
          </Card>
        ) : (
          <>
            <p className="mb-2 text-sm text-muted">{rows.length} asset{rows.length === 1 ? '' : 's'} in this group</p>
            <Card className="overflow-hidden">
              <AssetTable rows={rows} showGroup={false} onOpen={(a) => navigate(`/admin/assets/${a.id}`)} />
            </Card>
          </>
        )}
      </Page>

      {editing && (
        <AssetGroupFormModal
          group={g}
          companyId={g.companyId}
          companyName={g.companyName}
          onClose={() => setEditing(false)}
          onSaved={(saved) => {
            setEditing(false)
            if (!saved) navigate(backTo, { replace: true }) // deleted
            else void group.refetch()
          }}
        />
      )}
    </>
  )
}
