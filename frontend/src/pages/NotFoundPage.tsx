import { Link } from 'react-router-dom'
import { Button } from '../components/ui/Button'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-canvas text-center">
      <p className="text-6xl font-bold text-brand-600">404</p>
      <p className="text-muted">That page doesn't exist.</p>
      <Link to="/"><Button variant="secondary">Back to dashboard</Button></Link>
    </div>
  )
}
