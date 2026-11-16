import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Check, ChevronDown, FileText, MessageSquareQuote } from 'lucide-react'
import { cn } from '../../lib/cn'
import { templatesApi } from '../../api/templates'
import type { TemplateKind, TicketTemplate } from '../../types'

/**
 * Dropdown of the templates the current user may pick from.
 *
 * It renders nothing at all when the list is empty — an installation with no templates should
 * not grow a permanently disabled control on its new-ticket form.
 *
 * What a customer is offered is decided entirely by the backend from their own identity, so
 * `companyId` here is only a hint for staff choosing a company; passing it can never widen what
 * comes back.
 */
export function TemplatePicker({ kind, companyId, onPick, className, label }: {
  kind: TemplateKind
  companyId?: number
  onPick: (t: TicketTemplate) => void
  className?: string
  label?: string
}) {
  const [open, setOpen] = useState(false)
  const [applied, setApplied] = useState<number | null>(null)
  const ref = useRef<HTMLDivElement>(null)

  const templates = useQuery({
    queryKey: ['templates', 'usable', kind, companyId ?? null],
    queryFn: () => templatesApi.usable(kind, companyId),
    // Templates change when an admin edits them, not minute to minute — no need for the app's poll.
    refetchInterval: false,
    staleTime: 5 * 60_000,
  })

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    // Capture phase: Modal stops mousedown from bubbling (so a click inside it doesn't close it),
    // which would otherwise also hide every outside-click from this listener.
    document.addEventListener('mousedown', onDown, true)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDown, true); document.removeEventListener('keydown', onKey) }
  }, [open])

  const options = templates.data ?? []
  if (options.length === 0) return null

  const pick = (t: TicketTemplate) => {
    onPick(t)
    setApplied(t.id)
    setOpen(false)
    void templatesApi.markUsed(t.id)
  }

  const Icon = kind === 'TICKET' ? FileText : MessageSquareQuote

  return (
    <div ref={ref} className={cn('relative', className)}>
      <button
        type="button"
        onClick={(e) => { e.preventDefault(); setOpen((o) => !o) }}
        className="flex items-center gap-1.5 rounded-lg border border-line bg-surface px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
      >
        <Icon size={15} className="text-muted" />
        {label ?? (kind === 'TICKET' ? 'Use a template' : 'Canned reply')}
        <ChevronDown size={14} className="text-muted" />
      </button>

      {open && (
        <div className="absolute z-30 mt-1 max-h-80 w-80 overflow-y-auto rounded-xl border border-line bg-surface p-1 shadow-lg">
          {options.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={(e) => { e.preventDefault(); pick(t) }}
              className="flex w-full items-start gap-2 rounded-lg px-3 py-2 text-left hover:bg-gray-50"
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{t.name}</p>
                {t.description && <p className="line-clamp-2 text-xs text-muted">{t.description}</p>}
              </div>
              {applied === t.id && <Check size={15} className="mt-0.5 shrink-0 text-green-600" />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
