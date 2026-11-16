import { lazy, type ComponentType } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/useAuth'
import { RedirectIfAuthed, RequireAuth, RequireRole, RequireRoleOrPrivilege } from './auth/guards'
import { KbGate } from './pages/kb/KbGate'
import { ToastProvider } from './components/ui/Toast'
import { UiPrefsProvider } from './theme/UiPrefsProvider'
import { AppShell } from './components/layout/AppShell'
import LoginPage from './pages/auth/LoginPage'
import TokenPasswordPage from './pages/auth/TokenPasswordPage'
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage'
import ChangePasswordRequiredPage from './pages/auth/ChangePasswordRequiredPage'
import NotFoundPage from './pages/NotFoundPage'

// Every screen behind sign-in is its own chunk, loaded on first visit: the login page no longer
// downloads recharts and every admin screen up front. AppShell wraps its <Outlet/> in <Suspense>.
//
// After a deploy, a tab that was already open still points at the OLD chunk file names, which
// no longer exist — the import fails. Reload once to pick up the new index.html instead of
// showing a blank screen (the timestamp guard stops a reload loop if the chunk is truly gone).
function lazyPage<T extends ComponentType<object>>(load: () => Promise<{ default: T }>) {
  return lazy(() =>
    load().catch((err: unknown) => {
      const KEY = 'konoha:chunk-reload'
      let last = 0
      try { last = Number(sessionStorage.getItem(KEY) ?? 0) } catch { /* storage blocked */ }
      if (Date.now() - last > 10_000) {
        try { sessionStorage.setItem(KEY, String(Date.now())) } catch { /* storage blocked */ }
        window.location.reload()
        return new Promise<{ default: T }>(() => {}) // never resolves; the page is reloading
      }
      throw err
    }),
  )
}

const DashboardPage = lazyPage(() => import('./pages/dashboard/DashboardPage'))
const TicketsPage = lazyPage(() => import('./pages/tickets/TicketsPage'))
const TicketDetailPage = lazyPage(() => import('./pages/tickets/TicketDetailPage'))
const AnalyticsPage = lazyPage(() => import('./pages/analytics/AnalyticsPage'))
const CompaniesPage = lazyPage(() => import('./pages/companies/CompaniesPage'))
const CompanyDetailPage = lazyPage(() => import('./pages/companies/CompanyDetailPage'))
const CompanyFormPage = lazyPage(() => import('./pages/companies/CompanyFormPage'))
const UsersPage = lazyPage(() => import('./pages/admin/UsersPage'))
const PlansPage = lazyPage(() => import('./pages/admin/PlansPage'))
const RolesPage = lazyPage(() => import('./pages/admin/RolesPage'))
const AuditLogPage = lazyPage(() => import('./pages/admin/AuditLogPage'))
const TemplatesPage = lazyPage(() => import('./pages/admin/TemplatesPage'))
const AutomationPage = lazyPage(() => import('./pages/automation/AutomationPage'))
const ContractsPage = lazyPage(() => import('./pages/contracts/ContractsPage'))
const AssetCompaniesPage = lazyPage(() => import('./pages/assets/AssetCompaniesPage'))
const AssetsPage = lazyPage(() => import('./pages/assets/AssetsPage'))
const AssetDetailPage = lazyPage(() => import('./pages/assets/AssetDetailPage'))
const AssetGroupDetailPage = lazyPage(() => import('./pages/assets/AssetGroupDetailPage'))
const ClientAssetsPage = lazyPage(() => import('./pages/client/ClientAssetsPage'))
const ContractDetailPage = lazyPage(() => import('./pages/contracts/ContractDetailPage'))
const ClientOverviewPage = lazyPage(() => import('./pages/client/ClientOverviewPage'))
const ClientTicketsPage = lazyPage(() => import('./pages/client/ClientTicketsPage'))
const ClientReportsPage = lazyPage(() => import('./pages/client/ClientReportsPage'))
const NewTicketPage = lazyPage(() => import('./pages/client/NewTicketPage'))
const SettingsPage = lazyPage(() => import('./pages/client/SettingsPage'))
const StaffHomePage = lazyPage(() => import('./pages/dashboard/StaffHomePage'))
const KbHomePage = lazyPage(() => import('./pages/kb/KbHomePage'))
const KbArticlePage = lazyPage(() => import('./pages/kb/KbArticlePage'))
const KbManagePage = lazyPage(() => import('./pages/kb/KbManagePage'))
const KbEditorPage = lazyPage(() => import('./pages/kb/KbEditorPage'))
const KbHistoryPage = lazyPage(() => import('./pages/kb/KbHistoryPage'))
const KbCategoriesPage = lazyPage(() => import('./pages/kb/KbCategoriesPage'))

/**
 * Live data (ticket lists / counts, notifications) polls every 20s while the tab is visible, so
 * cross-portal changes — a new comment, a status change from the other side, an assignment —
 * show up without a refresh. Reference data (companies, users, plans, roles, templates…) no
 * longer polls: it is refreshed after the mutations that change it (invalidateQueries), and
 * again when you come back to the tab once it's older than 30 seconds. That removes most of the
 * background API traffic the old "everything every 15s" default generated.
 * Any query can still override refetchInterval (the ticket detail page polls faster while open).
 */
const LIVE_QUERY_ROOTS = new Set(['tickets', 'notifications'])
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      refetchInterval: (query) =>
        LIVE_QUERY_ROOTS.has(String(query.queryKey[0])) && query.queryKey[1] !== 'assignees' ? 20_000 : false,
    },
  },
})

/**
 * Same URL, different screen per portal: Admin/Engineer, customer, or — for a Default-role
 * employee, who has no tickets, reports or customer screens — `defaultStaff` (home by default).
 */
function ByPortal({ staff, client, defaultStaff }: { staff: React.ReactNode; client: React.ReactNode; defaultStaff?: React.ReactNode }) {
  const { hasRole } = useAuth()
  if (hasRole('ADMIN', 'ENGINEER')) return <>{staff}</>
  if (hasRole('STAFF')) return <>{defaultStaff ?? <Navigate to="/" replace />}</>
  return <>{client}</>
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
                {/* Outside the shell: the default admin can't call anything else until this is done. */}
                <Route path="change-password" element={<ChangePasswordRequiredPage />} />
                <Route element={<AppShell />}>
                  <Route index element={<ByPortal staff={<DashboardPage />} client={<ClientOverviewPage />} defaultStaff={<StaffHomePage />} />} />
                  <Route path="tickets" element={<ByPortal staff={<TicketsPage />} client={<ClientTicketsPage />} />} />
                  {/* No tickets for the Default employee role — the API refuses them anyway. */}
                  <Route element={<RequireRole roles={['ADMIN', 'ENGINEER', 'CUSTOMER_ADMIN', 'CUSTOMER']} />}>
                    <Route path="tickets/new" element={<NewTicketPage />} />
                    <Route path="tickets/:id" element={<TicketDetailPage />} />
                  </Route>
                  <Route path="companies/:id" element={<CompanyDetailPage />} />
                  {/* Automation: staff look at any company they can see, a Customer Admin at their own (plan-gated by the API). */}
                  <Route element={<RequireRole roles={['ADMIN', 'ENGINEER', 'CUSTOMER_ADMIN']} />}>
                    <Route path="automation" element={<AutomationPage />} />
                  </Route>
                  {/* Customers get their own company-scoped report, gated on the REPORTING feature. */}
                  <Route path="analytics" element={<ByPortal staff={<AnalyticsPage />} client={<ClientReportsPage />} />} />
                  <Route path="reports" element={<ByPortal staff={<AnalyticsPage />} client={<ClientReportsPage />} />} />
                  {/* Sits OUTSIDE the MANAGE_ASSETS guard on purpose: this is the customer's
                      own equipment list, and a customer never holds that privilege. Their access
                      is decided server-side by company scope plus the ASSET_MANAGEMENT plan
                      feature, which is the only thing that can actually enforce it. */}
                  <Route path="assets" element={<ByPortal staff={<Navigate to="/admin/assets" replace />} client={<ClientAssetsPage />} defaultStaff={<Navigate to="/admin/assets" replace />} />} />
                  <Route path="settings" element={<SettingsPage />} />
                  <Route path="profile" element={<SettingsPage />} />

                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN', 'ENGINEER']} privilege="MANAGE_COMPANIES" />}>
                    <Route path="companies" element={<CompaniesPage />} />
                  </Route>
                  <Route element={<RequireRole roles={['ADMIN', 'ENGINEER']} />}>
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
                    <Route path="admin/assets/groups/:groupId" element={<AssetGroupDetailPage />} />
                    <Route path="admin/assets/:id" element={<AssetDetailPage />} />
                  </Route>

                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_ROLES" />}>
                    <Route path="admin/roles" element={<RolesPage />} />
                  </Route>

                  {/* Knowledge Base. Readers: staff on /kb, customers on /help — KbGate sends a
                      link meant for the other side to the same article on this side. */}
                  <Route path="kb" element={<KbGate base="/kb" />}>
                    <Route index element={<KbHomePage />} />
                    <Route path=":slug" element={<KbArticlePage />} />
                  </Route>
                  <Route path="help" element={<KbGate base="/help" />}>
                    <Route index element={<KbHomePage />} />
                    <Route path=":slug" element={<KbArticlePage />} />
                  </Route>
                  {/* Writers: Admin, Engineers, MANAGE_KNOWLEDGE_BASE. Only managers publish — the
                      API enforces that; the editor shows Engineers "Request publish" instead. */}
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN', 'ENGINEER']} privilege="MANAGE_KNOWLEDGE_BASE" />}>
                    <Route path="admin/kb" element={<KbManagePage />} />
                    <Route path="admin/kb/new" element={<KbEditorPage />} />
                    <Route path="admin/kb/:id/edit" element={<KbEditorPage />} />
                    <Route path="admin/kb/:id/history" element={<KbHistoryPage />} />
                  </Route>
                  <Route element={<RequireRoleOrPrivilege roles={['ADMIN']} privilege="MANAGE_KNOWLEDGE_BASE" />}>
                    <Route path="admin/kb/categories" element={<KbCategoriesPage />} />
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
