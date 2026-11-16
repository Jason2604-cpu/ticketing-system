import { Link, Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Building2, CalendarClock, HardDrive } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card } from '../../components/ui/Card'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { assetsApi } from '../../api/assets'
import { api, errorMessage } from '../../lib/api'
import type { AssetCompanyRow } from '../../types'

/**
 * Assets → the company picker.
 *
 * Asset management is company-first on purpose. An asset saved against the wrong company is the
 * one mistake in this module that is genuinely painful to unpick — it breaks the contract link,
 * the ticket link and the customer's own view at once — so the company is a place you are in
 * rather than a dropdown you fill in, and the "wrong company" case stops existing instead of
 * being validated against.
 *
 * The warranty counts turn what would be a navigation step into a status board: this page answers
 * "who do I need to call about a renewal" before anyone clicks into anything.
 *
 * Scoping is entirely server-side — an engineer gets the companies they're assigned to, an admin
 * gets all of them.
 */
export default function AssetCompaniesPage() {
  const companies = useQuery({ queryKey: ['assets', 'companies'], queryFn: assetsApi.companies })
  const rows = companies.data ?? []

  // One company means the picker is a door with nothing to choose. Skip it — but only once the
  // data is actually in, or a slow network would bounce the page on every visit.
  if (!companies.isLoading && rows.length === 1) {
    return <Navigate to={`/admin/assets/company/${rows[0].companyId}`} replace />
  }

  return (
    <>
      <Topbar
        title="Assets"
        subtitle="Customer equipment, warranties and coverage"
        search={false}
        newTicket={false}
      />
      <Page>
        {companies.isError ? (
          <Alert tone="error">{errorMessage(companies.error)}</Alert>
        ) : companies.isLoading ? (
          <Card><div className="p-10 text-center text-sm text-muted">Loading companies…</div></Card>
        ) : rows.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Building2 size={22} />}
              title="No companies to show"
              hint="Asset registers live under a customer company. Once you're assigned to one, it appears here."
            />
          </Card>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {rows.map((c) => <CompanyCard key={c.companyId} row={c} />)}
          </div>
        )}
      </Page>
    </>
  )
}

function CompanyCard({ row }: { row: AssetCompanyRow }) {
  const logo = api.mediaUrl(row.logoUrl)
  const needsAttention = row.expiredCount > 0 || row.expiringSoonCount > 0

  return (
    <Link to={`/admin/assets/company/${row.companyId}`} className="block focus:outline-none">
      <Card className="h-full p-5 transition-shadow hover:shadow-md">
        <div className="flex items-start gap-3">
          {logo
            ? <img src={logo} alt="" className="h-10 w-10 shrink-0 rounded-lg object-cover" />
            : <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600"><Building2 size={18} /></span>}
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium text-ink">{row.companyName}</p>
            <p className="mt-0.5 flex items-center gap-1.5 text-sm text-muted">
              <HardDrive size={14} />
              {row.assetCount === 0 ? 'No assets yet' : `${row.assetCount} asset${row.assetCount === 1 ? '' : 's'}`}
            </p>
          </div>
        </div>

        {/* Only rendered when there is something to act on. A row of zeroes on every card trains
            people to stop reading the numbers, which defeats the point of putting them here. */}
        {needsAttention && (
          <div className="mt-4 flex flex-wrap gap-2 border-t border-line pt-3">
            {row.expiredCount > 0 && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-red-100 px-2.5 py-1 text-xs font-medium text-red-600">
                <AlertTriangle size={12} /> {row.expiredCount} out of warranty
              </span>
            )}
            {row.expiringSoonCount > 0 && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-100 px-2.5 py-1 text-xs font-medium text-brand-700">
                <CalendarClock size={12} /> {row.expiringSoonCount} expiring soon
              </span>
            )}
          </div>
        )}
      </Card>
    </Link>
  )
}
