import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import { notificationsApi } from '../../api/notifications'
import { timeAgo } from '../../lib/format'
import { cn } from '../../lib/cn'
import type { AppNotification } from '../../types'

/** Reload-free notifications: polls the backend and pops a real browser notification for anything new. */
export function NotificationBell() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const seen = useRef<Set<number> | null>(null) // null = not primed yet (first load shouldn't re-notify old items)

  const { data = [] } = useQuery({ queryKey: ['notifications'], queryFn: notificationsApi.list, refetchInterval: 10_000 })
  const unread = data.filter((n) => !n.read).length

  const markRead = useMutation({ mutationFn: notificationsApi.markRead, onSuccess: () => void qc.invalidateQueries({ queryKey: ['notifications'] }) })
  const markAllRead = useMutation({ mutationFn: notificationsApi.markAllRead, onSuccess: () => void qc.invalidateQueries({ queryKey: ['notifications'] }) })

  // Close on outside click.
  useEffect(() => {
    const onDoc = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  // Fire a real browser notification for anything unread we haven't shown yet.
  useEffect(() => {
    if (seen.current === null) {
      seen.current = new Set(data.map((n) => n.id))
      return
    }
    const fresh = data.filter((n) => !n.read && !seen.current!.has(n.id))
    for (const n of fresh) {
      seen.current.add(n.id)
      if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
        const browserNotif = new Notification(n.title, { body: n.body, tag: `ticket-notif-${n.id}` })
        browserNotif.onclick = () => { window.focus(); if (n.ticketId) navigate(`/tickets/${n.ticketId}`) }
      }
    }
    for (const n of data) seen.current.add(n.id)
  }, [data, navigate])

  const toggle = () => {
    if (!open && typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'default') {
      void Notification.requestPermission()
    }
    setOpen((o) => !o)
  }

  const openItem = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.id)
    setOpen(false)
    if (n.ticketId) navigate(`/tickets/${n.ticketId}`)
  }

  return (
    <div className="relative" ref={ref}>
      <button onClick={toggle} className="relative rounded-lg p-2 text-gray-600 hover:bg-gray-100" aria-label="Notifications">
        <Bell size={18} />
        {unread > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white ring-2 ring-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-80 rounded-xl border border-line bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <p className="text-sm font-semibold">Notifications</p>
            {unread > 0 && (
              <button onClick={() => markAllRead.mutate()} className="flex items-center gap-1 text-xs font-medium text-brand-600 hover:underline">
                <CheckCheck size={13} /> Mark all read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {data.length === 0 && <p className="px-4 py-8 text-center text-sm text-muted">No notifications yet.</p>}
            {data.map((n) => (
              <button key={n.id} onClick={() => openItem(n)}
                className={cn('flex w-full flex-col gap-0.5 border-b border-line px-4 py-3 text-left last:border-0 hover:bg-gray-50', !n.read && 'bg-brand-50/40')}>
                <div className="flex items-center gap-2">
                  {!n.read && <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-brand-600" />}
                  <p className="truncate text-sm font-medium">{n.title}</p>
                </div>
                <p className="line-clamp-2 text-xs text-muted">{n.body}</p>
                <p className="text-[11px] text-muted">{timeAgo(n.createdAt)}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
