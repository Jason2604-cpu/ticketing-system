import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'
import { cn } from '../../lib/cn'

type Toast = { id: number; tone: 'success' | 'error'; message: string }
import { ToastContext } from './toastContext'

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([])
  const toast = useCallback((message: string, tone: Toast['tone'] = 'success') => {
    const id = Date.now() + Math.random()
    setItems((s) => [...s, { id, tone, message }])
    setTimeout(() => setItems((s) => s.filter((t) => t.id !== id)), 3500)
  }, [])
  const value = useMemo(() => ({ toast }), [toast])
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed bottom-5 right-5 z-[60] flex flex-col gap-2">
        {items.map((t) => (
          <div key={t.id} className={cn('pointer-events-auto flex items-center gap-2 rounded-lg px-4 py-3 text-sm shadow-lg',
            t.tone === 'success' ? 'bg-toast text-toast-fg' : 'bg-red-600 text-white')}>
            {t.tone === 'success' ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
