import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ExternalLink, Lightbulb } from 'lucide-react'
import { kbApi } from '../../api/kb'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { useFeature } from '../../lib/useFeature'
import { cn } from '../../lib/cn'
import { ArticlePreviewModal } from './ArticlePreviewModal'

/** Ask only once the title says something: two words, or a long single word. */
const meaningful = (q: string) => q.split(/\s+/).filter((w) => w.length > 1).length >= 2 || q.length >= 8

/**
 * Articles that might answer the ticket being written, under its title. Customers open one over
 * the form (and can drop the ticket if it helped); staff, raising a ticket for a customer, get it
 * in a new tab. Renders nothing at all until there is something to suggest.
 */
export function SuggestedArticles({ title, companyId, staff = false, onSolved, className }: {
  title: string
  /** Staff: the company the ticket is for — suggestions are narrowed to what applies to it. */
  companyId?: number | null
  staff?: boolean
  /** Customer pressed "This solved it". */
  onSolved?: () => void
  className?: string
}) {
  const hasKb = useFeature('KNOWLEDGE_BASE')
  const q = useDebouncedValue(title.trim(), 400)
  const [open, setOpen] = useState<string | null>(null)
  const enabled = hasKb && meaningful(q)
  const suggestions = useQuery({
    queryKey: ['kb', 'suggest', q, companyId ?? null],
    queryFn: () => kbApi.suggest(q, companyId),
    enabled,
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  })
  const rows = enabled ? suggestions.data ?? [] : []
  if (rows.length === 0) return null

  return (
    <div className={cn('rounded-xl border border-brand-200 bg-brand-50 p-4', className)}>
      <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-brand-700">
        <Lightbulb size={16} /> {staff ? 'Knowledge Base articles that may already answer this' : 'These articles might help'}
      </p>
      <ul className="space-y-1">
        {rows.map((a) => (
          <li key={a.id}>
            {staff ? (
              <a href={`/kb/${a.slug}`} target="_blank" rel="noreferrer" className="group flex items-start gap-2 rounded-lg px-2 py-1.5 hover:bg-surface">
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-medium text-ink group-hover:underline">{a.title}</span>
                  {a.summary && <span className="block truncate text-xs text-muted">{a.summary}</span>}
                </span>
                <ExternalLink size={13} className="mt-1 shrink-0 text-muted" />
              </a>
            ) : (
              <button type="button" onClick={() => { setOpen(a.slug); void kbApi.feedback(a.slug, 'SUGGESTION_OPENED') }}
                className="group block w-full rounded-lg px-2 py-1.5 text-left hover:bg-surface">
                <span className="block text-sm font-medium text-ink group-hover:underline">{a.title}</span>
                {a.summary && <span className="block truncate text-xs text-muted">{a.summary}</span>}
              </button>
            )}
          </li>
        ))}
      </ul>
      {open && (
        <ArticlePreviewModal slug={open} base="/help" onClose={() => setOpen(null)}
          onSolved={onSolved ? () => { void kbApi.feedback(open, 'TICKET_AVOIDED'); setOpen(null); onSolved() } : undefined} />
      )}
    </div>
  )
}
