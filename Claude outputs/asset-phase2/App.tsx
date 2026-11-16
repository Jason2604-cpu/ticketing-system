import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/useAuth'
import { RedirectIfAuthed, RequireAuth, RequireRole, RequireRoleOrPrivilege } from './auth/guards'
import { ToastProvider } from './components/ui/Toast'
import { UiPrefsProvider } from './theme/UiPrefsProvider'
import { AppShell } from './components/layout/AppShell'
import LoginPage from './pages/auth/LoginPage'
import TokenPasswordPage from './pages/auth/TokenPasswordPage'
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage'
import DashboardPage from './pages/dashboard/DashboardPage'
import TicketsPage from './pages/tickets/TicketsPage'
import TicketDetailPage from './pages/tickets/TicketDetailPage'
import AnalyticsPage from './pages/analytics/AnalyticsPage'
import CompaniesPage from './pages/companies/CompaniesPage'
import CompanyDetailPage from './pages/companies/CompanyDetailPage'
import CompanyFormPage from './pages/companies/CompanyFormPage'
import UsersPage from './pages/admin/UsersPage'
import PlansPage from './pages/admin/PlansPage'
import RolesPage from './pages/admin/RolesPage'
import AuditLogPage from './pages/admin/AuditLogPage'
import TemplatesPage from './pages/admin/TemplatesPage'
import ContractsPage from './pages/contracts/ContractsPage'
import AssetCompaniesPage from './pages/assets/AssetCompaniesPage'
import AssetsPage from './pages/assets/AssetsPage'
import ContractDetailPage from './pages/contracts/ContractDetailPage'
import ClientOverviewPage from './pages/client/ClientOverviewPage'
import ClientTicketsPage from './pages/client/ClientTicketsPage'
import ClientReportsPage from './pages/client/ClientReportsPage'
import NewTicketPage from './pages/client/NewTicketPage'
import SettingsPage from './pages/client/SettingsPage'
import NotFoundPage from './pages/NotFoundPage'

const queryClient = new QueryClient({
  // Poll every 15s by default (paused while the tab is in the background) so cross-portal
  // changes — an admin assigning/unassigning a company to an engineer, a new comment, a status
  // change from the other side — show up without anyone hitting refresh. Any page can override
  // this per-query (e.g. the ticket detail page polls faster while open).
  defaultOptions: { queries: { retry: 1, staleTime: 15_000, refetchInterval: 15_000, refetchOnWindowFocus: false } },
})

/** Same URL, different screen per portal (staff vs client). */
function ByPortal({ staff, client }: { staff: React.ReactNode; client: React.ReactNode }) {
  const { hasRole } = useAuth()
  return <>{hasRole('ADMIN', 'ENGINEER') ? staff : client}</>
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <UiPrefsProvider>
        <ToastProvider>
        <BrowserRouter>
          <AuthProvider>
            <Routes>
              <Route element={<RedirectIfAuthed />}>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              </Route>
              {/* Email-link landing pages (work whether or not someone is signed in) */}
              <Route path="/activate" element={<TokenPasswordPage mode="activate" />} />
              <Route path="/reset-password" element={<TokenPasswordPage mode="reset" />} />

              <Route element={<RequireAuth />}>
                <Route element={<AppShell />}>
                  <Route index element={<ByPortal staff={<DashboardPage />} client={<ClientOverviewPage />} />} />
                  <Route path="tickets" element={<ByPortal staff={<TicketsPage />} client={<ClientTicketsPage />} />} />
                  <Route path="tickets/new" element={<NewTicketPage />} />
                  <Route path="tickets/:id" element={<TicketDetailPage />} />
                  <Route path="companies/:id" element={<CompanyDetailPage />} />
                  {/* Customers get their own company-scoped report, gated on the REPORTING feature. */}
                  <Route path="analytics" element={<ByPortal staff={<AnalyticsPage />} client={<ClientReportsPage />} />} />
                  <Route path="reports" element={<ByPortal staff={<AnalyticsPage />} client={<ClientReportsPage />} />} />
                  <Route path="settings" element={<SettingsPage />} />
                  <Route path="profile" element={<SettingsPage />} />

                  <Route element={<RequireRole roles={['ADMIN', 'ENGINEER']} />}>
                    <Route path="companies" element={<CompaniesPage />} />
                    {/* Plain forwards — the destination route enforces its own access. */}
                    <Route path="admin/engineers" element={<Navigate to="/admin/users" replace />} />
                    <Route path="admin/settings" element={<Navigate to="/admin/plans" replace />} />
                    <Route path="admin" element={<Navigate to="/admin/users" replace />} />
                  </Route>

                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_COMPANIES" />}>
                    <Route path="companies/new" element={<CompanyFormPage />} />
                  </Route>
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_USERS" />}>
                    <Route path="admin/users" element={<UsersPage />} />
                  </Route>
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_PLANS" />}>
                    <Route path="admin/plans" element={<PlansPage />} />
                  </Route>
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="VIEW_AUDIT_LOG" />}>
                    <Route path="admin/audit" element={<AuditLogPage />} />
                  </Route>

                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_TEMPLATES" />}>
                    <Route path="admin/templates" element={<TemplatesPage />} />
                  </Route>

                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_CONTRACTS" />}>
                    <Route path="admin/contracts" element={<ContractsPage />} />
                    <Route path="admin/contracts/:id" element={<ContractDetailPage />} />
                    {/* Shorter alias — the sidebar links to /admin/contracts, but /contracts reads naturally. */}
                    <Route path="contracts" element={<Navigate to="/admin/contracts" replace />} />
                  </Route>

                  {/* Assets are company-first: the picker, then one company's register. The
                      literal "company" segment ranks above a bare :id in react-router 7, so
                      /admin/assets/:id (phase 3) can sit alongside it without ambiguity. */}
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN', 'ENGINEER']} privilege="MANAGE_ASSETS" />}>
                    <Route path="admin/assets" element={<AssetCompaniesPage />} />
                    <Route path="admin/assets/company/:companyId" element={<AssetsPage />} />
                    <Route path="assets" element={<Navigate to="/admin/assets" replace />} />
                  </Route>

                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_ROLES" />}>
                    <Route path="admin/roles" element={<RolesPage />} />
                  </Route>
                </Route>
              </Route>

              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
        </ToastProvider>
      </UiPrefsProvider>
    </QueryClientProvider>
  )
}
