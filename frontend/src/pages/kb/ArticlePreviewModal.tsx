import { useQuery } from '@tanstack/react-query'
import { CircleCheck } from 'lucide-react'
import { Modal } from '../../components/ui/Modal'
import { Button } from '../../components/ui/Button'
import { Alert } from '../../components/ui/Misc'
import { kbApi } from '../../api/kb'
import { errorMessage } from '../../lib/api'
import { MarkdownView } from './MarkdownView'
import type { KbBase } from './kbAccess'

/**
 * An article opened from the new-ticket form, over the form rather than instead of it — so a
 * half-written ticket survives "this didn't help".
 */
export function ArticlePreviewModal({ slug, base, onClose, onSolved }: {
  slug: string
  base: KbBase
  onClose: () => void
  /** "This solved it — discard my ticket". Omit to show only Close. */
  onSolved?: () => void
}) {
  const article = useQuery({
    queryKey: ['kb', 'article', slug],
    queryFn: () => kbApi.article(slug),
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  })
  const a = article.data
  return (
    <Modal open onClose={onClose} size="xl" title={a?.title ?? 'Article'}
      footer={onSolved ? <>
        <Button variant="secondary" onClick={onClose}>I still need help</Button>
        <Button onClick={onSolved}><CircleCheck size={16} /> This solved it — discard my ticket</Button>
      </> : <Button variant="secondary" onClick={onClose}>Close</Button>}>
      {article.isError ? <Alert>{errorMessage(article.error)}</Alert>
        : !a ? <p className="py-10 text-center text-sm text-muted">Loading…</p>
        : (
          <div className="max-h-[60vh] overflow-y-auto pr-1">
            {a.summary && <p className="mb-4 text-muted">{a.summary}</p>}
            <MarkdownView source={a.body} base={base} />
          </div>
        )}
    </Modal>
  )
}
