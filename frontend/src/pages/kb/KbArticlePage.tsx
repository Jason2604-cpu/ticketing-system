import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, BookOpen, Eye, History, Lock, MessageSquarePlus, SquarePen, Ticket } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { StatusBadge as TicketStatusBadge } from '../../components/ui/Badge'
import { kbApi } from '../../api/kb'
import { errorMessage, isApiError } from '../../lib/api'
import { formatLongDate } from '../../lib/format'
import { useKbAccess } from './kbAccess'
import { CategoryIcon, ScopeBadges, VisibilityBadge } from './KbBadges'
import { MarkdownView } from './MarkdownView'

/**
 * One published article — /kb/:slug for staff, /help/:slug for customers. Always the PUBLISHED
 * version: an engineer's unpublished edits never show here, only a note to writers that they exist.
 */
export default function KbArticlePage() {
  const { slug = '' } = useParams()
  const { isCustomer, isWriter, base } = useKbAccess()
  const homeLabel = isCustomer ? 'Help Centre' : 'Knowledge Base'

  const article = useQuery({
    queryKey: ['kb', 'article', slug],
    queryFn: () => kbApi.article(slug),
    // Every fetch counts as a view, so don't refetch behind the reader's back.
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
    retry: (n, e) => !(isApiError(e) && (e.status === 403 || e.status === 404)) && n < 1,
  })

  // Writers: the tickets this article has been linked to (only the ones they can open).
  const a0 = article.data
  const linked = useQuery({
    queryKey: ['kb', 'manage', 'linked-tickets', a0?.id],
    queryFn: () => kbApi.linkedTickets(a0!.id),
    enabled: !!a0 && isWriter && !!a0.canEdit,
  })

  const back = (
    <Link to={base} className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:underline">
      <ArrowLeft size={15} /> {homeLabel}
    </Link>
  )

  if (article.isError) {
    const status = isApiError(article.error) ? article.error.status : 0
    return (
      <>
        <Topbar title={homeLabel} search={false} />
        <Page>
          <div className="mx-auto max-w-3xl space-y-4">
            {back}
            <Card>
              <CardBody>
                {status === 404 ? (
                  <EmptyState icon={<BookOpen size={22} />} title="Article not found"
                    hint="It may have been archived, renamed, or it isn't available to your account." />
                ) : status === 403 ? (
                  <EmptyState icon={<Lock size={22} />} title="The Help Centre isn't included in your plan"
                    hint="Talk to your account engineer to upgrade — or raise a ticket and we'll help directly." />
                ) : (
                  <Alert>{errorMessage(article.error)}</Alert>
                )}
              </CardBody>
            </Card>
          </div>
        </Page>
      </>
    )
  }

  const a = article.data
  return (
    <>
      <Topbar
        title={homeLabel}
        search={false}
        actions={a && isWriter && a.canEdit ? (
          <Link to={`/admin/kb/${a.id}/edit`}><Button variant="secondary"><SquarePen size={16} /> Edit</Button></Link>
        ) : undefined}
      />
      <Page>
        <div className="mx-auto max-w-3xl space-y-4">
          {back}
          {!a ? (
            <Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card>
          ) : (
            <>
              {!isCustomer && a.hasUnpublishedChanges && (
                <Alert tone="warning">
                  This article has unpublished changes. Readers see version {a.version} until an Admin or Knowledge Base manager publishes them.
                  {a.canEdit && <> <Link to={`/admin/kb/${a.id}/edit`} className="font-medium underline">Open the draft</Link></>}
                </Alert>
              )}
              <Card>
                <CardBody className="pt-6">
                  {a.category && (
                    <Link to={`${base}?category=${a.category.id}`} className="mb-3 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:underline">
                      <CategoryIcon name={a.category.icon} size={14} /> {a.category.name}
                    </Link>
                  )}
                  <h1 className="text-2xl font-semibold leading-tight tracking-tight">{a.title}</h1>
                  {a.summary && <p className="mt-2 text-[15px] text-muted">{a.summary}</p>}

                  <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-line pb-4 text-xs text-muted">
                    <span>Updated {formatLongDate(new Date(a.publishedAt))}</span>
                    {!isCustomer && (
                      <>
                        <span>· Version {a.version}{a.publishedBy ? `, published by ${a.publishedBy.name}` : ''}</span>
                        <VisibilityBadge visibility={a.visibility} />
                        <ScopeBadges companies={a.companies} max={3} />
                        {a.viewCount != null && <span className="inline-flex items-center gap-1"><Eye size={12} /> {a.viewCount}</span>}
                        {isWriter && a.canEdit && (
                          <Link to={`/admin/kb/${a.id}/history`} className="inline-flex items-center gap-1 text-brand-600 hover:underline"><History size={12} /> History</Link>
                        )}
                      </>
                    )}
                  </div>

                  <MarkdownView source={a.body} base={base} className="mt-5" />
                </CardBody>
              </Card>

              {!isCustomer && (linked.data?.length ?? 0) > 0 && (
                <Card>
                  <CardHeader title={`Linked to ${linked.data!.length} ticket${linked.data!.length === 1 ? '' : 's'}`} />
                  <CardBody>
                    <ul className="divide-y divide-line">
                      {linked.data!.map((t) => (
                        <li key={t.id} className="flex items-center gap-3 py-2 text-sm">
                          <Ticket size={15} className="shrink-0 text-muted" />
                          <Link to={`/tickets/${t.id}`} className="min-w-0 flex-1 truncate font-medium text-brand-600 hover:underline">#{t.id} {t.title}</Link>
                          <span className="hidden text-xs text-muted sm:inline">{t.companyName}</span>
                          <TicketStatusBadge status={t.status} />
                        </li>
                      ))}
                    </ul>
                  </CardBody>
                </Card>
              )}

              {isCustomer && (
                <Card>
                  <CardBody className="flex flex-wrap items-center justify-between gap-3 pt-5">
                    <div>
                      <p className="font-semibold">Still stuck?</p>
                      <p className="text-sm text-muted">Raise a ticket and an engineer will pick it up.</p>
                    </div>
                    <Link to="/tickets/new"><Button><MessageSquarePlus size={16} /> Raise a ticket</Button></Link>
                  </CardBody>
                </Card>
              )}
            </>
          )}
        </div>
      </Page>
    </>
  )
}
