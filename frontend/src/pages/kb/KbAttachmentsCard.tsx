import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText, Paperclip, Plus, Trash2 } from 'lucide-react'
import { Card, CardBody, CardHeader } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { GenericBadge } from '../../components/ui/Badge'
import { useToast } from '../../components/ui/useToast'
import { kbApi } from '../../api/kb'
import { api, errorMessage } from '../../lib/api'
import { formatBytes } from '../../lib/format'
import type { KbAttachment } from '../../types'
import { attachmentMarkdown } from './kbMarkdown'

/**
 * The article's images and files. Uploading adds the file and inserts it at the end of the draft;
 * readers only get a file once a published version links it. Files aren't versioned, so deleting
 * one the published version shows breaks it for readers — the confirm says so.
 */
export function KbAttachmentsCard({ articleId, canEdit, onUpload, onInsert }: {
  articleId: number
  canEdit: boolean
  onUpload: (file: File) => Promise<string>
  onInsert: (markdown: string) => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const input = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const list = useQuery({ queryKey: ['kb', 'manage', 'attachments', articleId], queryFn: () => kbApi.attachments(articleId) })
  const remove = useMutation({
    mutationFn: (a: KbAttachment) => kbApi.removeAttachment(articleId, a.id),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['kb', 'manage', 'attachments', articleId] }); toast('File deleted') },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const pick = async (files: File[]) => {
    setUploading(true)
    for (const f of files) {
      try { onInsert(await onUpload(f)) } catch (e) { toast(`${f.name}: ${errorMessage(e)}`, 'error') }
    }
    setUploading(false)
  }

  const rows = list.data ?? []
  return (
    <Card>
      <CardHeader title={`Images & files${rows.length ? ` (${rows.length})` : ''}`}
        action={canEdit && <Button size="sm" variant="secondary" loading={uploading} onClick={() => input.current?.click()}><Paperclip size={14} /> Upload</Button>} />
      <CardBody>
        <input ref={input} type="file" hidden multiple accept=".png,.jpg,.jpeg,.webp,.pdf,.csv,.txt"
          onChange={(e) => { void pick([...(e.target.files ?? [])]); e.target.value = '' }} />
        {rows.length === 0 ? (
          <p className="text-sm text-muted">Paste or drop images into the editor, or upload here. PNG, JPG, WebP, PDF, CSV or TXT up to 20 MB.</p>
        ) : (
          <ul className="divide-y divide-line">
            {rows.map((a) => (
              <li key={a.id} className="flex items-center gap-3 py-2">
                {a.image
                  ? <img src={api.mediaUrl(a.url)} alt="" className="h-10 w-10 shrink-0 rounded-md border border-line object-cover" />
                  : <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-gray-100 text-muted"><FileText size={16} /></span>}
                <div className="min-w-0 flex-1">
                  <a href={api.mediaUrl(a.url)} target="_blank" rel="noreferrer" className="block truncate text-sm font-medium text-ink hover:underline" title={a.name}>{a.name}</a>
                  <span className="flex items-center gap-1.5 text-xs text-muted">
                    {formatBytes(a.sizeBytes)}
                    {a.inPublished ? <GenericBadge tone="green">Published</GenericBadge> : !a.inDraft && <GenericBadge tone="gray">Not used</GenericBadge>}
                  </span>
                </div>
                {canEdit && (
                  <>
                    <button type="button" title="Insert into the article" aria-label={`Insert ${a.name}`} onClick={() => onInsert(attachmentMarkdown(a))}
                      className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink"><Plus size={15} /></button>
                    <button type="button" title="Delete" aria-label={`Delete ${a.name}`}
                      onClick={() => confirm(a.inPublished
                        ? `The published version shows "${a.name}". Deleting it breaks that for readers until a version without it is published. Delete anyway?`
                        : `Delete "${a.name}"?`) && remove.mutate(a)}
                      className="rounded-md p-1.5 text-muted hover:bg-red-50 hover:text-red-600"><Trash2 size={15} /></button>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  )
}
