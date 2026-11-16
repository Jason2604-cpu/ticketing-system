import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { errorMessage } from '../../lib/api'
import { AuthLayout } from './AuthLayout'
import { Button } from '../../components/ui/Button'
import { Field, Input } from '../../components/ui/Input'
import { Alert } from '../../components/ui/Misc'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true); setError(null)
    try { await authApi.forgotPassword(email); setSent(true) } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }

  return (
    <AuthLayout title="Reset your password" subtitle="We'll email you a link to choose a new one.">
      {sent ? (
        <Alert tone="success">If an account exists for <b>{email}</b>, a reset link is on its way. It expires in 30 minutes.</Alert>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          {error && <Alert>{error}</Alert>}
          <Field label="Email"><Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          <Button type="submit" className="w-full" loading={busy}>Send reset link</Button>
        </form>
      )}
      <p className="mt-4 text-center text-sm"><Link to="/login" className="font-medium text-brand-600 hover:underline">Back to sign in</Link></p>
    </AuthLayout>
  )
}
