import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { diffLines } from 'diff'
import { ArrowLeft, History, RotateCcw } from 'lucide-react'
import { Topbar } from '../../components/layout/Topbar'
import { Page } from '../../components/layout/AppShell'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState } from '../../components/ui/Misc'
import { SelectMenu } from '../../components/ui/SelectMenu'
import { useToast } from '../../components/ui/useToast'
import { kbApi } from '../../api/kb'
import { errorMessage } from '../../lib/api'
import { formatShortDate } from '../../lib/format'
import { cn } from '../../lib/cn'
import type { KbManageArticle, KbVersion } from '../../types'
import { useKbAccess } from './kbAccess'
import { GenericBadge } from '../../components/ui/Badge'
import { MarkdownView } from './MarkdownView'

type Compare = 'previous' | 'draft'

/**
 * Every published version of an article, oldest at the bottom. Open one to read it or to see what
 * changed — against the version before it, or against today's draft — and restore it into the
 * draft. Restoring never changes what readers see: it's an edit, and publishing is the decision.
 */
export default function KbHistoryPage() {
  const { id } = useParams()
  const articleId = Number(id)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { base } = useKbAccess()

  const article = useQuery({ queryKey: ['kb', 'manage', 'article', articleId], queryFn: () => kbApi.manageGet(articleId) })
  const versions = useQuery({ queryKey: ['kb', 'manage', 'versions', articleId], queryFn: () => kbApi.versions(articleId) })
  const [selected, setSelected] = useState<number | null>(null)
  const [view, setView] = useState<'read' | 'changes'>('changes')
  const [compare, setCompare] = useState<Compare>('previous')

  const list = versions.data ?? []
  const number = selected ?? list[0]?.version ?? null
  const previousNumber = list.find((v) => number != null && v.version < number)?.version ?? null

  const version = useQuery({
    queryKey: ['kb', 'manage', 'version', articleId, number],
    queryFn: () => kbApi.version(articleId, number!),
    enabled: number != null,
  })
  const previous = useQuery({
    queryKey: ['kb', 'manage', 'version', articleId, previousNumber],
    queryFn: () => kbApi.version(articleId, previousNumber!),
    enabled: previousNumber != null && compare === 'previous',
  })

  const restore = useMutation({
    mutationFn: (n: number) => kbApi.restore(articleId, n),
    onSuccess: (a, n) => {
      qc.setQueryData(['kb', 'manage', 'article', a.id], a)
      void qc.invalidateQueries({ queryKey: ['kb', 'manage', 'list'] })
      toast(`Version ${n} copied into the draft — publish it to make it live`)
      navigate(`/admin/kb/${a.id}/edit`)
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const a = article.data
  const v = version.data
  const base2 = compare === 'previous' ? previous.data ?? null : null

  return (
    <>
      <Topbar
        title="Version history"
        subtitle={a?.title}
        search={false}
        newTicket={false}
        actions={<Link to={`/admin/kb/${articleId}/edit`}><Button variant="secondary"><ArrowLeft size={16} /> Back to editor</Button></Link>}
      />
      <Page>
        {article.isError || versions.isError ? (
          <Alert>{errorMessage(article.error ?? versions.error)}</Alert>
        ) : list.length === 0 && !versions.isLoading ? (
          <Card><EmptyState icon={<History size={22} />} title="Not published yet" hint="Every publish adds a version here." /></Card>
        ) : (
          <div className="grid grid-cols-1 gap-6 xl:grid-cols-[300px_minmax(0,1fr)]">
            <Card className="self-start">
              <CardHeader title="Versions" />
              <div className="divide-y divide-line">
                {list.map((x) => (
                  <button key={x.version} type="button" onClick={() => setSelected(x.version)}
                    className={cn('block w-full px-5 py-3 text-left transition-colors', x.version === number ? 'bg-brand-50' : 'hover:bg-gray-50')}>
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">Version {x.version}</span>
                      {x.current && <GenericBadge tone="green">Live</GenericBadge>}
                    </div>
                    <p className="mt-0.5 text-xs text-muted">{formatShortDate(x.publishedAt)}{x.publishedBy ? ` · ${x.publishedBy.name}` : ''}</p>
                    {x.changeNote && <p className="mt-1 line-clamp-2 text-sm text-gray-700">{x.changeNote}</p>}
                  </button>
                ))}
              </div>
            </Card>

            <div className="min-w-0 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="inline-flex rounded-lg border border-line bg-surface p-1">
                  {(['changes', 'read'] as const).map((m) => (
                    <button key={m} type="button" onClick={() => setView(m)}
                      className={cn('rounded-md px-3 py-1.5 text-sm font-medium', view === m ? 'bg-brand-600 text-white' : 'text-gray-700 hover:bg-gray-50')}>
                      {m === 'changes' ? 'What changed' : 'Read this version'}
                    </button>
                  ))}
                </div>
                <div className="flex items-center gap-2">
                  {view === 'changes' && (
                    <SelectMenu<Compare> className="w-60" size="sm" value={compare} onChange={setCompare}
                      options={[{ value: 'previous', label: 'Against the version before' }, { value: 'draft', label: 'Against today’s draft' }]} />
                  )}
                  {v && a?.permissions.canEdit && (
                    <Button size="sm" variant="secondary" loading={restore.isPending}
                      onClick={() => confirm(`Copy version ${v.version} into the draft? Your current draft is replaced (it stays in this history only if it was published). Readers keep seeing the live version until it's published again.`) && restore.mutate(v.version)}>
                      <RotateCcw size={15} /> Restore into draft
                    </Button>
                  )}
                </div>
              </div>

              {!v ? (
                <Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card>
              ) : view === 'read' ? (
                <Card>
                  <CardBody className="pt-6">
                    <h2 className="text-xl font-semibold">{v.title}</h2>
                    {v.summary && <p className="mt-1 text-muted">{v.summary}</p>}
                    <MarkdownView source={v.body ?? ''} base={base} className="mt-5" />
                  </CardBody>
                </Card>
              ) : (
                <Changes
                  from={compare === 'previous' ? base2 : v}
                  to={compare === 'previous' ? v : a ?? null}
                  fromLabel={compare === 'previous' ? (previousNumber ? `Version ${previousNumber}` : 'nothing (first version)') : `Version ${v.version}`}
                  toLabel={compare === 'previous' ? `Version ${v.version}` : 'Today’s draft'}
                  loading={compare === 'previous' && previousNumber != null && previous.isLoading}
                />
              )}
            </div>
          </div>
        )}
      </Page>
    </>
  )
}

type Comparable = Pick<KbVersion, 'title' | 'summary' | 'visibility' | 'category' | 'companies'> & { body?: string | null }
const asComparable = (x: KbVersion | KbManageArticle | null): Comparable | null =>
  x ? { title: x.title, summary: x.summary, body: x.body, visibility: x.visibility, category: x.category, companies: x.companies } : null

/** Field changes as a short list, then a line diff of the body. */
function Changes({ from, to, fromLabel, toLabel, loading }: {
  from: KbVersion | KbManageArticle | null; to: KbVersion | KbManageArticle | null; fromLabel: string; toLabel: string; loading: boolean
}) {
  const a = asComparable(from)
  const b = asComparable(to)
  const parts = useMemo(() => diffLines(a?.body ?? '', b?.body ?? ''), [a?.body, b?.body])
  if (loading || !b) return <Card><div className="p-10 text-center text-sm text-muted">Loading…</div></Card>

  const scope = (x: Comparable | null) => (x?.companies?.length ? x.companies.map((c) => c.name).join(', ') : 'All companies')
  const fields: [string, string, string][] = [
    ['Title', a?.title ?? '', b.title],
    ['Summary', a?.summary ?? '', b.summary ?? ''],
    ['Visibility', a?.visibility ?? '', b.visibility],
    ['Category', a?.category?.name ?? (a ? 'Uncategorised' : ''), b.category?.name ?? 'Uncategorised'],
    ['Available to', a ? scope(a) : '', scope(b)],
  ]
  const changed = fields.filter(([, x, y]) => x !== y)
  const bodyChanged = parts.some((p) => p.added || p.removed)

  return (
    <Card>
      <CardBody className="space-y-4 pt-5">
        <p className="text-sm text-muted">From <strong className="text-ink">{fromLabel}</strong> to <strong className="text-ink">{toLabel}</strong></p>
        {changed.length === 0 && !bodyChanged && <p className="text-sm">No differences.</p>}
        {changed.length > 0 && (
          <div className="space-y-1.5">
            {changed.map(([label, x, y]) => (
              <div key={label} className="grid grid-cols-[110px_minmax(0,1fr)] gap-2 text-sm">
                <span className="font-medium text-muted">{label}</span>
                <span>
                  {x && <del className="mr-2 rounded bg-red-50 px-1 text-red-700">{x}</del>}
                  <ins className="rounded bg-green-50 px-1 text-green-700 no-underline">{y || '—'}</ins>
                </span>
              </div>
            ))}
          </div>
        )}
        {bodyChanged && (
          <pre className="overflow-x-auto rounded-lg border border-line font-mono text-xs leading-5">
            {parts.map((p, i) => {
              const lines = p.value.replace(/\n$/, '').split('\n')
              const sign = p.added ? '+ ' : p.removed ? '- ' : '  '
              // Long unchanged stretches fold to their first and last lines.
              const folded = !p.added && !p.removed && lines.length > 8
              const shown = folded ? [...lines.slice(0, 3), null, ...lines.slice(-3)] : lines
              return (
                <span key={i} className={cn('block whitespace-pre-wrap px-3', p.added && 'bg-green-50 text-green-700', p.removed && 'bg-red-50 text-red-700')}>
                  {shown.map((line, j) => line === null
                    ? <span key={j} className="block select-none text-muted">  … {lines.length - 6} unchanged lines</span>
                    : <span key={j} className="block">{sign}{line}</span>)}
                </span>
              )
            })}
          </pre>
        )}
      </CardBody>
    </Card>
  )
}
