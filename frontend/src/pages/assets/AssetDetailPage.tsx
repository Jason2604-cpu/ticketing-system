import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BellRing, CalendarClock, HardDrive, MapPin, Pencil, Trash2, User as UserIcon, UserX } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { GenericBadge } from '../../components/ui/Badge'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { assetsApi } from '../../api/assets'
import { errorMessage } from '../../lib/api'
import { formatDateOnly } from '../../lib/format'
import { STATUS_TONE, WARRANTY_TONE, warrantyPhrase } from './assetMeta'
import { AssetFormModal } from './AssetFormModal'

/**
 * One asset.
 *
 * Deliberately thin for now: the ticket history and the covering contracts are the two cards that
 * will make this page worth opening, and they land in phases 6 and 7 with the links themselves.
 * Stubbing them as empty cards now would promise something that isn't there.
 */
export default function AssetDetailPage() {
  const { id } = useParams()
  const assetId = Number(id)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState(false)

  const asset = useQuery({
    queryKey: ['assets', 'detail', assetId],
    queryFn: () => assetsApi.get(assetId),
    enabled: Number.isFinite(assetId) && assetId > 0,
  })

  const a = asset.data

  const remove = useMutation({
    mutationFn: () => assetsApi.remove(assetId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['assets'] })
      toast('Asset deleted')
      // Back to the company's register, not the picker — that's where you were.
      navigate(a ? `/admin/assets/company/${a.companyId}` : '/admin/assets')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  if (asset.isLoading) {
    return <><Topbar title="Asset" search={false} newTicket={false} /><Page><Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card></Page></>
  }
  if (asset.isError || !a) {
    return <><Topbar title="Asset" search={false} newTicket={false} /><Page><Alert>{asset.error ? errorMessage(asset.error) : "Couldn't load this asset. It may have been deleted."}</Alert></Page></>
  }

  return (
    <>
      <Topbar
        title={a.name}
        subtitle={`${a.companyName}${a.assetTag ? ` · ${a.assetTag}` : ''}`}
        search={false}
        newTicket={false}
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setEditing(true)}><Pencil size={15} /> Edit</Button>
            <Button
              variant="danger"
              loading={remove.isPending}
              onClick={() => confirm(`Delete "${a.name}"? If it's referenced by a ticket or a contract this will be refused — set it to Retired instead.`) && remove.mutate()}
            >
              <Trash2 size={15} /> Delete
            </Button>
          </div>
        }
      />
      <Page>
        <Link to={`/admin/assets/company/${a.companyId}`} className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:underline">
          <ArrowLeft size={15} /> {a.companyName} assets
        </Link>

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
          <div className="space-y-5 lg:col-span-2">
            <Card>
              <CardHeader title={<span className="flex items-center gap-2"><HardDrive size={16} /> Details</span>} />
              <CardBody className="space-y-3 pt-0 text-sm">
                <Row label="Type">{a.typeLabel}</Row>
                <Row label="Make / model">{[a.make, a.model].filter(Boolean).join(' ') || <Dash />}</Row>
                <Row label="Serial number">{a.serialNumber ?? <Dash />}</Row>
                <Row label="Location">
                  {a.location ? <span className="inline-flex items-center gap-1.5"><MapPin size={14} className="text-muted" /> {a.location}</span> : <Dash />}
                </Row>
                <Row label="Asset group">
                  {a.groupId
                    ? <Link to={`/admin/assets/groups/${a.groupId}`} className="text-brand-700 hover:underline">{a.groupName}</Link>
                    : <Dash />}
                </Row>
                <Row label="Status"><GenericBadge tone={STATUS_TONE[a.status]}>{a.statusLabel}</GenericBadge></Row>
                <Row label="Purchased">{formatDateOnly(a.purchaseDate)}</Row>
              </CardBody>
            </Card>

            {a.notes && (
              <Card>
                <CardHeader title="Notes" />
                <CardBody className="pt-0"><p className="whitespace-pre-wrap text-sm">{a.notes}</p></CardBody>
              </Card>
            )}
          </div>

          <div className="space-y-5">
            <Card>
              <CardHeader title={<span className="flex items-center gap-2"><UserIcon size={16} /> Assigned to</span>} />
              <CardBody className="space-y-3 pt-0 text-sm">
                <p className="text-base font-medium">{a.usedBy}</p>
                <p className="text-xs text-muted">{a.assignedToLabel}</p>
                {a.assignedTo === 'SYSTEM_USER' && a.ownerEmail && (
                  <p className="break-all text-sm text-muted">{a.ownerEmail}</p>
                )}
                {a.assignedTo === 'GUEST' && a.guestEmail && (
                  <p className="break-all text-sm text-muted">{a.guestEmail}</p>
                )}
                {/* Surfaced rather than cleaned up: "what hardware do we need back from this
                    person" is exactly the question asked when somebody leaves. */}
                {a.ownerInactive && (
                  <Alert tone="warning" className="flex items-center gap-2">
                    <UserX size={15} /> This person's account is no longer active.
                  </Alert>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title={<span className="flex items-center gap-2"><CalendarClock size={16} /> Warranty</span>} />
              <CardBody className="space-y-3 pt-0 text-sm">
                <Row label="Standing">
                  <GenericBadge tone={WARRANTY_TONE[a.warrantyState]}>{a.warrantyStateLabel}</GenericBadge>
                </Row>
                <Row label="Starts">{formatDateOnly(a.warrantyStart)}</Row>
                <Row label="Ends">
                  {a.warrantyEnd ? (
                    <span>
                      {formatDateOnly(a.warrantyEnd)}
                      {typeof a.daysToWarrantyEnd === 'number' && (
                        <span className="block text-xs text-muted">{warrantyPhrase(a.daysToWarrantyEnd)}</span>
                      )}
                    </span>
                  ) : <Dash />}
                </Row>
                <Row label="Reminder">
                  {a.warrantyReminderEnabled ? (
                    <span className="inline-flex items-center gap-1.5 text-brand-600">
                      <BellRing size={14} /> {a.warrantyReminderDays} days before
                    </span>
                  ) : <span className="text-muted">Off</span>}
                </Row>
              </CardBody>
            </Card>
          </div>
        </div>
      </Page>

      {editing && (
        <AssetFormModal
          asset={a}
          companyId={a.companyId}
          companyName={a.companyName}
          onClose={() => setEditing(false)}
          onSaved={() => setEditing(false)}
        />
      )}
    </>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="shrink-0 text-muted">{label}</span>
      <span className="min-w-0 text-right">{children}</span>
    </div>
  )
}

const Dash = () => <span className="text-gray-400">—</span>
