import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { authApi } from '../../api/auth'
import { errorMessage } from '../../lib/api'
import { AuthLayout } from './AuthLayout'
import { Button } from '../../components/ui/Button'
import { Field, Input } from '../../components/ui/Input'
import { Alert } from '../../components/ui/Misc'

/**
 * Shown right after signing in with a password the user didn't choose — the built-in default
 * admin (admin@admin.com / admin). The API refuses everything except this until it's done, so
 * RequireAuth routes here from anywhere while `user.mustChangePassword` is set.
 */
export default function ChangePasswordRequiredPage() {
  const { user, refresh, logout } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user && !user.mustChangePassword) return <Navigate to="/" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (next !== confirm) { setError('New passwords do not match'); return }
    setBusy(true); setError(null)
    try {
      await authApi.changePassword(current, next)
      await refresh()
      navigate('/', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Set a new password" subtitle={`You're signed in as ${user?.email ?? ''} with the default password. Choose your own to continue.`}>
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <Field label="Current password"><Input type="password" autoComplete="current-password" required value={current} onChange={(e) => setCurrent(e.target.value)} /></Field>
        <Field label="New password" hint="At least 10 characters with upper, lower, a digit and a symbol.">
          <Input type="password" autoComplete="new-password" required value={next} onChange={(e) => setNext(e.target.value)} />
        </Field>
        <Field label="Confirm new password"><Input type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} /></Field>
        <Button type="submit" className="w-full" loading={busy}>Save and continue</Button>
        <p className="text-center text-sm text-muted">
          <button type="button" onClick={() => logout()} className="font-medium text-brand-600 hover:underline">Sign out</button>
        </p>
      </form>
    </AuthLayout>
  )
}
