import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { errorMessage } from '../../lib/api'
import { AuthLayout } from './AuthLayout'
import { Button } from '../../components/ui/Button'
import { Field, Input } from '../../components/ui/Input'
import { Alert } from '../../components/ui/Misc'

/** Shared screen for /activate and /reset-password: emailed token + choose a password. */
export default function TokenPasswordPage({ mode }: { mode: 'activate' | 'reset' }) {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const copy = mode === 'activate'
    ? { title: 'Activate your account', subtitle: 'Confirm your email and choose the password you will sign in with.', cta: 'Activate account' }
    : { title: 'Choose a new password', subtitle: 'Your previous password will stop working immediately.', cta: 'Update password' }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (password !== confirm) { setError('Passwords do not match'); return }
    setBusy(true); setError(null)
    try {
      if (mode === 'activate') await authApi.activate(token, password)
      else await authApi.resetPassword(token, password)
      navigate('/login', { replace: true, state: { notice: mode === 'activate' ? 'Account activated — you can sign in now.' : 'Password updated — sign in with your new password.' } })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <AuthLayout title={copy.title}>
        <Alert>This link is missing its token. Please use the link from your email.</Alert>
        <p className="mt-4 text-center text-sm"><Link to="/login" className="font-medium text-brand-600 hover:underline">Back to sign in</Link></p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title={copy.title} subtitle={copy.subtitle}>
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <Field label="New password" hint="At least 10 characters with upper, lower, a digit and a symbol.">
          <Input type="password" autoComplete="new-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Confirm password"><Input type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} /></Field>
        <Button type="submit" className="w-full" loading={busy}>{copy.cta}</Button>
      </form>
    </AuthLayout>
  )
}
