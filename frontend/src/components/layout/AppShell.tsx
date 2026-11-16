import { Suspense, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { portalFor } from './portal'
import { useAuth } from '../../auth/useAuth'
import { Spinner } from '../ui/Spinner'

/** Sidebar + scrollable content column. Sets the portal theme (accent colour) from the user's role. */
export function AppShell() {
  const { user } = useAuth()
  const portal = portalFor(user?.role)
  useEffect(() => {
    document.documentElement.dataset.portal = portal
    return () => { delete document.documentElement.dataset.portal }
  }, [portal])

  return (
    <div className="flex h-screen overflow-hidden" data-portal={portal}>
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        {/* Pages are lazy-loaded chunks (App.tsx); keep the sidebar up while one downloads. */}
        <Suspense fallback={<div className="flex flex-1 items-center justify-center"><Spinner /></div>}>
          <Outlet />
        </Suspense>
      </div>
    </div>
  )
}

export function Page({ children }: { children: React.ReactNode }) {
  return <main className="flex-1 overflow-y-auto px-8 py-6">{children}</main>
}
