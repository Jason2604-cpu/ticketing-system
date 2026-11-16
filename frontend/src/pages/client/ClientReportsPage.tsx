import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Lock, Printer } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Button } from '../../components/ui/Button'
import { Card, CardBody } from '../../components/ui/Card'
import { Spinner } from '../../components/ui/Spinner'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { DateRangePicker, defaultRange } from '../../components/ui/DateRangePicker'
import { useToast } from '../../components/ui/useToast'
import { ReportView } from '../analytics/ReportView'
import { reportsApi } from '../../api/reports'
import { errorMessage } from '../../lib/api'
import { useFeature } from '../../lib/useFeature'
import { useAuth } from '../../auth/useAuth'
import type { ReportRange } from '../../types'

/**
 * The customer-facing report, scoped to the signed-in user's own company.
 *
 * Gated on the REPORTING plan feature. The nav item is hidden without it, but this page still
 * checks — someone can always type the URL — and the API would refuse anyway.
 * Per-company and per-engineer breakdowns are hidden here: a customer has exactly one company,
 * and which of the helpdesk's engineers is fastest is internal information.
 */
export default function ClientReportsPage() {
  const toast = useToast()
  const { user } = useAuth()
  const hasReporting = useFeature('REPORTING')
  const [range, setRange] = useState<ReportRange>(defaultRange)

  const report = useQuery({
    queryKey: ['report', 'mine', range.from, range.to],
    // A report is a set of GROUP BY scans; riding the app's global 15s poll would re-run them
    // constantly for a page nobody expects to be live. Refresh on navigation instead.
    refetchInterval: false,
    staleTime: 60_000,
    queryFn: () => reportsApi.summary(range),
    enabled: hasReporting,
  })

  if (!hasReporting) {
    return (
      <>
        <Topbar title="Reports" search={false} />
        <Page>
          <Card>
            <CardBody>
              <EmptyState
                icon={<Lock size={22} />}
                title="Reporting isn't included in your plan"
                hint="Ticket reports, SLA compliance and trend charts are available on the Professional and Enterprise plans. Talk to your account engineer to upgrade."
              />
            </CardBody>
          </Card>
        </Page>
      </>
    )
  }

  const exportCsv = async () => {
    try { await reportsApi.downloadCsv(range) } catch (e) { toast(errorMessage(e), 'error') }
  }

  return (
    <>
      <Topbar
        title="Reports"
        subtitle={report.data ? `${report.data.from} to ${report.data.to} · ${user?.companyName ?? ''}` : undefined}
        search={false}
        newTicket={false}
        actions={
          <div className="flex gap-2 print:hidden">
            <Button variant="secondary" size="sm" onClick={exportCsv}><Download size={15} /> CSV</Button>
            <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer size={15} /> PDF</Button>
          </div>
        }
      />
      <Page>
        <div className="mb-4 print:hidden">
          <DateRangePicker value={range} onChange={setRange} />
        </div>
        {report.isLoading && <Spinner />}
        {report.isError && <Alert tone="error">{errorMessage(report.error)}</Alert>}
        {report.data && <ReportView report={report.data} showCompanies={false} showEngineers={false} />}
      </Page>
    </>
  )
}
