import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/useAuth'
import { RedirectIfAuthed, RequireAuth, RequireRole, RequireRoleOrPrivilege } from './auth/guards'
import { ToastProvider } from './components/ui/Toast'
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
import ClientOverviewPage from './pages/client/ClientOverviewPage'
import ClientTicketsPage from './pages/client/ClientTicketsPage'
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
                  <Route path="analytics" element={<AnalyticsPage />} />
                  <Route path="settings" element={<SettingsPage />} />
                  <Route path="profile" element={<SettingsPage />} />

                  <Route element={<RequireRole roles={['ADMIN', 'ENGINEER']} />}>
                    <Route path="companies" element={<CompaniesPage />} />
                    {/* Plain forwards — the destination route enforces its own access. */}
                    <Route path="admin/engineers" element={<Navigate to="/admin/users" replace />} />
                    <Route path="admin/plans" element={<Navigate to="/admin/settings" replace />} />
                    <Route path="admin" element={<Navigate to="/admin/users" replace />} />
                  </Route>

                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_COMPANIES" />}>
                    <Route path="companies/new" element={<CompanyFormPage />} />
                  </Route>
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_USERS" />}>
                    <Route path="admin/users" element={<UsersPage />} />
                  </Route>
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_PLANS" />}>
                    <Route path="admin/settings" element={<PlansPage />} />
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
    </QueryClientProvider>
  )
}
