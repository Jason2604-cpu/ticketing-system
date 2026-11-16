import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Printer } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Alert } from '../../components/ui/Misc'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { DateRangePicker, defaultRange } from '../../components/ui/DateRangePicker'
import { useToast } from '../../components/ui/useToast'
import { ReportView } from './ReportView'
import { reportsApi } from '../../api/reports'
import { companiesApi } from '../../api/companies'
import { errorMessage } from '../../lib/api'
import type { ReportRange } from '../../types'

export default function AnalyticsPage() {
  const toast = useToast()
  const [range, setRange] = useState<ReportRange>(defaultRange)
  const [companyId, setCompanyId] = useState<string>('')

  const companies = useQuery({ queryKey: ['companies'], queryFn: companiesApi.list })
  const scope = companyId ? Number(companyId) : undefined
  const report = useQuery({
    queryKey: ['report', range.from, range.to, scope ?? 'all'],
    // A report is a set of GROUP BY scans; riding the app's global 15s poll would re-run them
    // constantly for a page nobody expects to be live. Refresh on navigation instead.
    refetchInterval: false,
    staleTime: 60_000,
    queryFn: () => reportsApi.summary(range, scope),
  })

  const exportCsv = async () => {
    try { await reportsApi.downloadCsv(range, scope) } catch (e) { toast(errorMessage(e), 'error') }
  }

  return (
    <>
      <Topbar
        title="Analytics & Reports"
        subtitle={report.data ? `${report.data.from} to ${report.data.to}${report.data.companyName ? ` · ${report.data.companyName}` : ''}` : undefined}
        search={false}
        actions={
          <div className="flex gap-2 print:hidden">
            <Button variant="secondary" size="sm" onClick={exportCsv}><Download size={15} /> CSV</Button>
            <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer size={15} /> PDF</Button>
          </div>
        }
      />
      <Page>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 print:hidden">
          <DateRangePicker value={range} onChange={setRange} />
          <div className="w-56">
            <SelectMenu
              value={companyId}
              onChange={setCompanyId}
              options={[{ value: '', label: 'All companies' },
                ...(companies.data ?? []).map((c) => ({ value: String(c.id), label: c.name }))]}
            />
          </div>
        </div>

        {report.isLoading && <Spinner />}
        {report.isError && <Alert tone="error">{errorMessage(report.error)}</Alert>}
        {report.data && <ReportView report={report.data} />}
      </Page>
    </>
  )
}
