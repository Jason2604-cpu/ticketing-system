import { Routes, Route } from 'react-router-dom'
import MainLayout from '../components/layout/MainLayout'
import ProtectedRoute from '../components/layout/ProtectedRoute'
import LoginPage from '../pages/auth/LoginPage'
import DashboardPage from '../pages/dashboard/DashboardPage'
import EngineerManagementPage from '../pages/engineers/EngineerManagementPage'
import NotFoundPage from '../pages/NotFoundPage'

const AppRoutes = () => {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
        <Route path="/"           element={<DashboardPage />} />
        <Route path="/engineers"  element={<EngineerManagementPage />} />
        <Route path="/companies"  element={<div>Companies</div>} />
        <Route path="/settings"   element={<div>Settings</div>} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default AppRoutes