import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, PenSquare, Plus, X } from 'lucide-react'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { GenericBadge } from '../../components/ui/Badge'
import { useToast } from '../../components/ui/useToast'
import { ticketArticlesApi } from '../../api/kb'
import { errorMessage } from '../../lib/api'
import { useKbAccess } from './kbAccess'
import { ArticlePickerModal } from './ArticlePickerModal'

/**
 * Knowledge Base articles linked to a ticket. Customers see only the ones they can open (and the
 * card not at all when there are none); staff link, unlink, and — once the ticket is resolved —
 * can turn it into a new article.
 */
export function RelatedArticlesCard({ ticketId, companyId, staff, resolved }: {
  ticketId: number
  companyId: number
  staff: boolean
  resolved: boolean
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const { base } = useKbAccess()
  const [picking, setPicking] = useState(false)
  const list = useQuery({ queryKey: ['kb', 'ticket', ticketId], queryFn: () => ticketArticlesApi.list(ticketId) })
  const unlink = useMutation({
    mutationFn: (articleId: number) => ticketArticlesApi.unlink(ticketId, articleId),
    onSuccess: (rows) => qc.setQueryData(['kb', 'ticket', ticketId], rows),
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const rows = list.data ?? []
  if (!staff && rows.length === 0) return null

  return (
    <Card>
      <CardHeader title="Related articles" action={staff && <Button size="sm" variant="secondary" onClick={() => setPicking(true)}><Plus size={14} /> Link</Button>} />
      <CardBody className="space-y-3">
        {rows.length === 0 ? (
          <p className="text-sm text-muted">No articles linked. Link one that explains the fix, so the next person with this problem finds it.</p>
        ) : (
          <ul className="space-y-2">
            {rows.map((a) => (
              <li key={a.articleId} className="flex items-start gap-2">
                <BookOpen size={15} className="mt-0.5 shrink-0 text-muted" />
                <div className="min-w-0 flex-1">
                  {a.status && a.status !== 'PUBLISHED'
                    ? <Link to={`/admin/kb/${a.articleId}/edit`} className="text-sm font-medium text-brand-600 hover:underline">{a.title}</Link>
                    : <Link to={`${base}/${a.slug}`} className="text-sm font-medium text-brand-600 hover:underline">{a.title}</Link>}
                  {staff && (
                    <div className="mt-0.5 flex flex-wrap gap-1">
                      {a.status === 'DRAFT' && <GenericBadge tone="gray">Draft</GenericBadge>}
                      {a.visibleToCustomer === false && <GenericBadge tone="amber">Customer can’t open it</GenericBadge>}
                    </div>
                  )}
                </div>
                {staff && (
                  <button type="button" aria-label={`Unlink ${a.title}`} title="Unlink"
                    onClick={() => unlink.mutate(a.articleId)}
                    className="rounded-md p-1 text-muted hover:bg-gray-100 hover:text-ink"><X size={14} /></button>
                )}
              </li>
            ))}
          </ul>
        )}
        {staff && resolved && (
          <Link to={`/admin/kb/new?fromTicket=${ticketId}`} className="inline-flex items-center gap-1.5 border-t border-line pt-3 text-sm font-medium text-brand-600 hover:underline">
            <PenSquare size={14} /> Write an article from this ticket
          </Link>
        )}
      </CardBody>
      {picking && <ArticlePickerModal ticketId={ticketId} companyId={companyId} onClose={() => setPicking(false)} />}
    </Card>
  )
}
