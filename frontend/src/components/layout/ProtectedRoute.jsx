import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import authService from '../../services/authService'

const ProtectedRoute = ({ children }) => {

  // null = still checking, true = logged in, false = not
  const [authStatus, setAuthStatus] = useState(null)

  useEffect(() => {
    // Ask backend: "is my cookie still valid?"
    authService.checkAuth()
      .then(isAuth => setAuthStatus(isAuth))
      .catch(() => setAuthStatus(false))
  }, [])

  // Still checking — show nothing (or a spinner)
  if (authStatus === null) return <div>Loading...</div>

  // Not logged in — redirect to login
  if (authStatus === false) return <Navigate to="/login" replace />

  // Logged in — render the protected page
  return children
}

export default ProtectedRoute