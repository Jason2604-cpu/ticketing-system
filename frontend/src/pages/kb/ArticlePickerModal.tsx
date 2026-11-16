import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { GenericBadge } from '../../components/ui/Badge'
import { Alert } from '../../components/ui/Misc'
import { useToast } from '../../components/ui/useToast'
import { kbApi, ticketArticlesApi } from '../../api/kb'
import { companiesApi } from '../../api/companies'
import { errorMessage } from '../../lib/api'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import type { KbArticleSummary, KbTicketArticle } from '../../types'
import { VisibilityBadge } from './KbBadges'
import { articleReplyLine } from './kbMarkdown'

/**
 * Staff: find a published article for a ticket and link it — and, from the reply box, also put a
 * link to it into the reply. A customer must never be sent a link they can't open, so an Internal
 * article (or any article when their plan lacks the Help Centre) can only be linked for staff, or
 * inserted into an internal note.
 */
export function ArticlePickerModal({ ticketId, companyId, internal = false, onInsert, onClose }: {
  ticketId: number
  companyId: number
  /** The reply being written is an internal note. */
  internal?: boolean
  /** Set when opened from the reply box: receives the line to add to the reply. */
  onInsert?: (line: string) => void
  onClose: () => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const [input, setInput] = useState('')
  const q = useDebouncedValue(input.trim(), 300)
  const results = useQuery({
    queryKey: ['kb', 'suggest', q, companyId],
    queryFn: () => kbApi.suggest(q, companyId),
    enabled: q.length >= 2,
    staleTime: 60_000,
  })
  const feature = useQuery({
    queryKey: ['companies', companyId, 'feature', 'KNOWLEDGE_BASE'],
    queryFn: () => companiesApi.hasFeature(companyId, 'KNOWLEDGE_BASE'),
    staleTime: 5 * 60_000,
  })
  const linked = useQuery({ queryKey: ['kb', 'ticket', ticketId], queryFn: () => ticketArticlesApi.list(ticketId) })
  const linkedIds = new Set((linked.data ?? []).map((l) => l.articleId))

  const link = useMutation({
    mutationFn: (a: KbArticleSummary) => ticketArticlesApi.link(ticketId, a.id),
    onSuccess: (list: KbTicketArticle[]) => { qc.setQueryData(['kb', 'ticket', ticketId], list) },
  })
  const customerCanRead = (a: KbArticleSummary) => !!feature.data?.enabled && a.visibility === 'PUBLIC'

  const choose = async (a: KbArticleSummary, insert: boolean) => {
    try {
      if (!linkedIds.has(a.id)) await link.mutateAsync(a)
      if (insert && onInsert) onInsert(articleReplyLine(a.title, a.slug))
      toast(insert ? 'Article linked and added to your reply' : 'Article linked to the ticket')
      onClose()
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }

  const rows = q.length >= 2 ? results.data ?? [] : []
  return (
    <Modal open onClose={onClose} size="lg" title={onInsert ? 'Add an article to your reply' : 'Link an article'}>
      <div className="relative mb-4">
        <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
        <input autoFocus value={input} onChange={(e) => setInput(e.target.value)} placeholder="Search published articles…" aria-label="Search articles"
          className="h-10 w-full rounded-lg border border-line bg-surface pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100" />
      </div>
      {feature.data && !feature.data.enabled && (
        <Alert tone="info" className="mb-3">This customer’s plan doesn’t include the Help Centre, so they can’t open articles — link them for staff only.</Alert>
      )}
      {q.length < 2 ? (
        <p className="py-6 text-center text-sm text-muted">Type a few words from the problem — the best matches for this customer appear here.</p>
      ) : results.isLoading ? (
        <p className="py-6 text-center text-sm text-muted">Searching…</p>
      ) : rows.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted">No published article matches. Try other words.</p>
      ) : (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {rows.map((a) => {
            const readable = customerCanRead(a)
            const insertAllowed = readable || internal
            return (
              <li key={a.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                    {a.title} <VisibilityBadge visibility={a.visibility} />
                    {readable ? <GenericBadge tone="green">Customer can open it</GenericBadge> : <GenericBadge tone="amber">Staff only</GenericBadge>}
                  </p>
                  {a.summary && <p className="mt-0.5 line-clamp-1 text-xs text-muted">{a.summary}</p>}
                </div>
                <div className="flex gap-2">
                  {onInsert && (
                    <Button size="sm" disabled={!insertAllowed || link.isPending} onClick={() => void choose(a, true)}
                      title={insertAllowed ? 'Link it and add it to the reply' : 'The customer couldn’t open this — tick “Internal note” to add it, or link it only'}>
                      Add to reply
                    </Button>
                  )}
                  <Button size="sm" variant="secondary" disabled={linkedIds.has(a.id) || link.isPending} onClick={() => void choose(a, false)}>
                    {linkedIds.has(a.id) ? 'Linked' : onInsert ? 'Link only' : 'Link'}
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Modal>
  )
}
