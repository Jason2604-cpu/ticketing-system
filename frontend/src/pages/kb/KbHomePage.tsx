import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { BookOpen, ChevronLeft, ChevronRight, Clock, Lock, PenSquare, Search, TrendingUp, X } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState, SectionTitle } from '../../components/ui/Misc'
import { Spinner } from '../../components/ui/Spinner'
import { kbApi } from '../../api/kb'
import { errorMessage } from '../../lib/api'
import { formatShortDate } from '../../lib/format'
import { useFeature } from '../../lib/useFeature'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { cn } from '../../lib/cn'
import type { KbArticleSummary, KbCategory } from '../../types'
import { useKbAccess, useKbRequestedCount, type KbBase } from './kbAccess'
import { CategoryIcon, SnippetText, VisibilityBadge } from './KbBadges'

const PAGE_SIZE = 20

type Sort = 'recent' | 'popular'
const SORTS: { value: Sort; label: string; icon: React.ReactNode }[] = [
  { value: 'recent', label: 'Recently updated', icon: <Clock size={13} /> },
  { value: 'popular', label: 'Most viewed', icon: <TrendingUp size={13} /> },
]

/**
 * Knowledge Base home — /kb for staff, /help ("Help Centre") for customers. Search as you type,
 * browse by category, recently updated or most viewed first. Only published articles the caller
 * may read ever reach this page; the API decides that, this page just shows what comes back.
 */
export default function KbHomePage() {
  const { isCustomer, isWriter, isManager, base } = useKbAccess()
  const hasKb = useFeature('KNOWLEDGE_BASE')
  const waiting = useKbRequestedCount(isManager)
  const [params, setParams] = useSearchParams()
  const categoryId = params.get('category') ? Number(params.get('category')) : null
  const sort: Sort = params.get('sort') === 'popular' ? 'popular' : 'recent'

  // Typing updates the box at once; the URL (and the request) follow once typing pauses.
  const [input, setInput] = useState(params.get('q') ?? '')
  const q = useDebouncedValue(input.trim(), 300)
  // The page number belongs to one search + category; a new search starts at page 1 again.
  const listKey = `${q}|${categoryId ?? ''}|${sort}`
  const [paging, setPaging] = useState({ key: listKey, page: 0 })
  const page = paging.key === listKey ? paging.page : 0
  const setPage = (fn: (p: number) => number) => setPaging({ key: listKey, page: fn(page) })
  useEffect(() => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      if (q) next.set('q', q); else next.delete('q')
      return next
    }, { replace: true })
  }, [q, setParams])

  const setSort = (value: Sort) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      if (value === 'popular') next.set('sort', value); else next.delete('sort')
      return next
    }, { replace: true })
  }

  const setCategory = (id: number | null) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      if (id) next.set('category', String(id)); else next.delete('category')
      return next
    })
  }

  const categories = useQuery({ queryKey: ['kb', 'categories'], queryFn: kbApi.categories, enabled: hasKb })
  const articles = useQuery({
    queryKey: ['kb', 'articles', { q, categoryId, sort, page }],
    // A search is always best match first, so the sort only applies to browsing.
    queryFn: () => kbApi.articles({ q: q || undefined, categoryId, sort: q ? undefined : sort, page, size: PAGE_SIZE }),
    enabled: hasKb,
    placeholderData: keepPreviousData,
  })

  const title = isCustomer ? 'Help Centre' : 'Knowledge Base'

  if (!hasKb) {
    return (
      <>
        <Topbar title={title} search={false} />
        <Page>
          <Card>
            <CardBody>
              <EmptyState
                icon={<Lock size={22} />}
                title="The Help Centre isn't included in your plan"
                hint="Step-by-step answers to common questions are available on the Professional and Enterprise plans. Talk to your account engineer to upgrade — or raise a ticket and we'll help directly."
                action={<Link to="/tickets/new"><Button>Raise a ticket</Button></Link>}
              />
            </CardBody>
          </Card>
        </Page>
      </>
    )
  }

  const activeCategory = categories.data?.find((c) => c.id === categoryId)
  const visibleCategories = (categories.data ?? []).filter((c) => !isCustomer || c.articleCount > 0)
  const data = articles.data
  const searching = !!q

  return (
    <>
      <Topbar
        title={title}
        subtitle={isCustomer ? 'Answers to common questions — try here before raising a ticket' : 'Published articles: what staff, and for Public ones customers, can read'}
        search={false}
        actions={isWriter ? <Link to="/admin/kb"><Button variant="secondary"><PenSquare size={16} /> Manage articles</Button></Link> : undefined}
      />
      <Page>
        <div className="mx-auto max-w-5xl space-y-6">
          <div className="relative">
            <Search size={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-muted" />
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={isCustomer ? 'Describe your problem — e.g. “printer offline” or “reset VPN password”' : 'Search articles…'}
              aria-label="Search articles"
              autoFocus
              className="h-12 w-full rounded-xl border border-line bg-surface pl-11 pr-10 text-[15px] shadow-card placeholder:text-gray-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
            />
            {input && (
              <button type="button" onClick={() => setInput('')} aria-label="Clear search"
                className="absolute right-3 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted hover:bg-gray-100 hover:text-ink">
                <X size={16} />
              </button>
            )}
          </div>

          {isManager && waiting > 0 && (
            <Alert tone="info" className="flex flex-wrap items-center justify-between gap-2">
              <span>{waiting} article{waiting === 1 ? ' is' : 's are'} waiting for you to publish.</span>
              <Link to="/admin/kb?tab=requested" className="font-medium underline">Review {waiting === 1 ? 'it' : 'them'}</Link>
            </Alert>
          )}

          {!searching && !categoryId && visibleCategories.length > 0 && (
            <div>
              <SectionTitle className="mb-3">Browse by topic</SectionTitle>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {visibleCategories.map((c) => <CategoryTile key={c.id} category={c} onClick={() => setCategory(c.id)} />)}
              </div>
            </div>
          )}

          <div>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                {activeCategory && (
                  <button type="button" onClick={() => setCategory(null)} className="flex items-center gap-1 text-sm font-medium text-brand-600 hover:underline">
                    <ChevronLeft size={15} /> All topics
                  </button>
                )}
                <SectionTitle>
                  {searching
                    ? `${data?.totalElements ?? 0} result${data?.totalElements === 1 ? '' : 's'} for “${q}”${activeCategory ? ` in ${activeCategory.name}` : ''}`
                    : activeCategory ? activeCategory.name : 'Articles'}
                </SectionTitle>
                {articles.isFetching && <Spinner className="h-4 w-4" />}
              </div>
              {!searching && (
                <div className="inline-flex rounded-md border border-line bg-surface p-0.5 text-xs font-medium" role="group" aria-label="Order articles by">
                  {SORTS.map((o) => (
                    <button key={o.value} type="button" aria-pressed={sort === o.value} onClick={() => setSort(o.value)}
                      className={cn('flex items-center gap-1 rounded px-2 py-1', sort === o.value ? 'bg-brand-600 text-white' : 'text-gray-700 hover:bg-gray-50')}>
                      {o.icon} {o.label}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {articles.isError ? (
              <Alert>{errorMessage(articles.error)}</Alert>
            ) : articles.isLoading ? (
              <Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card>
            ) : !data || data.items.length === 0 ? (
              <Card>
                <EmptyState
                  icon={<BookOpen size={22} />}
                  title={searching ? 'No articles match that' : 'No articles here yet'}
                  hint={searching
                    ? (isCustomer ? 'Try fewer or different words — or raise a ticket and we’ll help directly.' : 'Try fewer or different words. Quotes search for an exact phrase; -word leaves a word out.')
                    : isWriter ? 'Articles appear here once they’re published.' : undefined}
                  action={isCustomer
                    ? <Link to="/tickets/new"><Button>Raise a ticket</Button></Link>
                    : isWriter ? <Link to="/admin/kb/new"><Button>Write an article</Button></Link> : undefined}
                />
              </Card>
            ) : (
              <Card className="divide-y divide-line">
                {data.items.map((a) => <ArticleRow key={a.id} article={a} base={base} staff={!isCustomer} />)}
              </Card>
            )}

            {data && data.totalPages > 1 && (
              <div className="mt-3 flex items-center justify-end gap-2 text-sm text-muted">
                <span>Page {data.page + 1} of {data.totalPages}</span>
                <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page"><ChevronLeft size={15} /></Button>
                <Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page"><ChevronRight size={15} /></Button>
              </div>
            )}
          </div>

          {isCustomer && (
            <p className="text-center text-sm text-muted">
              Can’t find what you need? <Link to="/tickets/new" className="font-medium text-brand-600 hover:underline">Raise a ticket</Link>
            </p>
          )}
        </div>
      </Page>
    </>
  )
}

function CategoryTile({ category, onClick }: { category: KbCategory; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick}
      className="flex items-start gap-3 rounded-xl border border-line bg-surface p-4 text-left shadow-card transition-shadow hover:shadow-md">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
        <CategoryIcon name={category.icon} size={19} />
      </span>
      <span className="min-w-0">
        <span className="block font-semibold">{category.name}</span>
        {category.description && <span className="mt-0.5 line-clamp-2 block text-sm text-muted">{category.description}</span>}
        <span className="mt-1 block text-xs text-muted">{category.articleCount} article{category.articleCount === 1 ? '' : 's'}</span>
      </span>
    </button>
  )
}

function ArticleRow({ article: a, base, staff }: { article: KbArticleSummary; base: KbBase; staff: boolean }) {
  return (
    <Link to={`${base}/${a.slug}`} className="block px-5 py-4 transition-colors hover:bg-gray-50">
      <div className="flex items-start justify-between gap-3">
        <p className="font-semibold text-ink">{a.title}</p>
        {staff && <VisibilityBadge visibility={a.visibility} />}
      </div>
      {a.snippet?.length
        ? <SnippetText parts={a.snippet} className="mt-1 line-clamp-2 text-sm text-muted" />
        : a.summary && <p className="mt-1 line-clamp-2 text-sm text-muted">{a.summary}</p>}
      <div className={cn('mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted')}>
        {a.category && <span className="inline-flex items-center gap-1"><CategoryIcon name={a.category.icon} size={12} /> {a.category.name}</span>}
        <span>Updated {formatShortDate(a.publishedAt)}</span>
        {staff && a.viewCount != null && <span>{a.viewCount} view{a.viewCount === 1 ? '' : 's'}</span>}
      </div>
    </Link>
  )
}
