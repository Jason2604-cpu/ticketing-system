import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Bold, Code, Eye, FileText, Heading2, Image, Italic, Link2, List, ListOrdered, Paperclip, Pencil, Quote, SquareCode, Table } from 'lucide-react'
import { cn } from '../../lib/cn'
import { MarkdownView } from './MarkdownView'
import { RichTextEditor } from './RichTextEditor'
import type { KbBase } from './kbAccess'

type Mode = 'rich' | 'write' | 'preview'
type Edit = { text: string; selStart: number; selEnd: number }
type ToolId = 'heading' | 'bold' | 'italic' | 'bullets' | 'numbers' | 'quote' | 'code' | 'codeblock' | 'link' | 'image' | 'file' | 'table'

const IMAGE_TYPES = '.png,.jpg,.jpeg,.webp'
const FILE_TYPES = '.png,.jpg,.jpeg,.webp,.pdf,.csv,.txt'

const TOOLS: { id: ToolId; icon: ReactNode; label: string }[] = [
  { id: 'heading', icon: <Heading2 size={15} />, label: 'Heading' },
  { id: 'bold', icon: <Bold size={15} />, label: 'Bold (Ctrl+B)' },
  { id: 'italic', icon: <Italic size={15} />, label: 'Italic (Ctrl+I)' },
  { id: 'bullets', icon: <List size={15} />, label: 'Bulleted list' },
  { id: 'numbers', icon: <ListOrdered size={15} />, label: 'Numbered list' },
  { id: 'quote', icon: <Quote size={15} />, label: 'Quote' },
  { id: 'code', icon: <Code size={15} />, label: 'Code' },
  { id: 'codeblock', icon: <SquareCode size={15} />, label: 'Code block' },
  { id: 'link', icon: <Link2 size={15} />, label: 'Link (Ctrl+K)' },
  { id: 'image', icon: <Image size={15} />, label: 'Image' },
  { id: 'file', icon: <Paperclip size={15} />, label: 'Attach a file' },
  { id: 'table', icon: <Table size={15} />, label: 'Table' },
]

/**
 * The article body editor, three views of the same Markdown:
 *
 *  - Editor (default): formatted text — RichTextEditor. Photos show as photos and attached files as
 *    chips, so there is no link syntax to break by deleting half of it.
 *  - Markdown: the raw text in a plain textarea with a small toolbar, for anyone who prefers it or
 *    needs to fix something by hand. Shortcuts: Ctrl/⌘+B bold, Ctrl/⌘+I italic, Ctrl/⌘+K link.
 *  - Preview: the same MarkdownView readers get, so what you see is what gets published.
 *
 * What is saved is always Markdown, which keeps articles diffable in the version history and free
 * of pasted HTML.
 *
 * With `onUpload`, images can be pasted or dropped straight into the text, and the Image / Attach
 * buttons upload from disk; each finished upload is inserted where the cursor is.
 */
export function MarkdownEditor({ value, onChange, base, maxLength = 100_000, placeholder, disabled, invalid, onUpload, onUploadError }: {
  value: string
  onChange: (v: string) => void
  base: KbBase
  maxLength?: number
  placeholder?: string
  disabled?: boolean
  invalid?: boolean
  /** Upload one file and return the Markdown that shows it (`![…](url)` or `[name](url)`). */
  onUpload?: (file: File) => Promise<string>
  onUploadError?: (message: string) => void
}) {
  const ref = useRef<HTMLTextAreaElement>(null)
  const imageInput = useRef<HTMLInputElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const [mode, setMode] = useState<Mode>('rich')
  // Set by the rich editor: puts a finished upload at its cursor.
  const richInsert = useRef<((markdown: string) => void) | null>(null)
  const [uploading, setUploading] = useState(0)
  const [dragging, setDragging] = useState(false)
  // Uploads finish after more typing may have happened, so they insert into the latest text.
  const latest = useRef(value)
  useEffect(() => { latest.current = value }, [value])

  const insertAtCursor = (snippet: string) => {
    const t = latest.current
    const ta = ref.current
    const at = ta ? ta.selectionStart : t.length
    const lead = at === 0 || t[at - 1] === '\n' ? '' : '\n'
    const text = t.slice(0, at) + lead + snippet + '\n' + t.slice(at)
    latest.current = text
    onChange(text)
    const caret = at + lead.length + snippet.length + 1
    if (ta) requestAnimationFrame(() => { ta.focus(); ta.setSelectionRange(caret, caret) })
  }

  const upload = async (files: File[]) => {
    if (!onUpload || disabled || files.length === 0) return
    setUploading((n) => n + files.length)
    for (const f of files) {
      try {
        const markdown = await onUpload(f)
        if (mode === 'rich' && richInsert.current) richInsert.current(markdown)
        else insertAtCursor(markdown)
      } catch (e) {
        onUploadError?.(`${f.name}: ${e instanceof Error ? e.message : 'upload failed'}`)
      } finally {
        setUploading((n) => n - 1)
      }
    }
  }

  const apply = (fn: (t: string, s: number, e: number) => Edit) => {
    const ta = ref.current
    if (!ta || disabled) return
    const r = fn(value, ta.selectionStart, ta.selectionEnd)
    onChange(r.text)
    requestAnimationFrame(() => { ta.focus(); ta.setSelectionRange(r.selStart, r.selEnd) })
  }

  /** Surround the selection (or a placeholder word, selected so it can be typed over). */
  const wrap = (before: string, after = before, fallback = 'text') => apply((t, s, e) => {
    const sel = t.slice(s, e) || fallback
    return { text: t.slice(0, s) + before + sel + after + t.slice(e), selStart: s + before.length, selEnd: s + before.length + sel.length }
  })

  /** Prefix every line the selection touches: headings, lists, quotes. */
  const prefixLines = (prefix: (i: number) => string) => apply((t, s, e) => {
    const start = t.lastIndexOf('\n', s - 1) + 1
    const endIdx = t.indexOf('\n', e)
    const end = endIdx === -1 ? t.length : endIdx
    const block = t.slice(start, end).split('\n').map((line, i) => prefix(i) + line).join('\n')
    return { text: t.slice(0, start) + block + t.slice(end), selStart: start, selEnd: start + block.length }
  })

  /** Insert a block on its own line; `pick` is the part to select afterwards. */
  const insertBlock = (block: string, pick: string) => apply((t, s, e) => {
    const lead = s === 0 || t[s - 1] === '\n' ? '' : '\n'
    const text = t.slice(0, s) + lead + block + t.slice(e)
    const at = s + lead.length + block.indexOf(pick)
    return { text, selStart: at, selEnd: at + pick.length }
  })

  const link = (image: boolean) => apply((t, s, e) => {
    const label = t.slice(s, e) || (image ? 'description' : 'link text')
    const ins = `${image ? '!' : ''}[${label}](https://)`
    const urlAt = s + ins.length - 'https://)'.length
    return { text: t.slice(0, s) + ins + t.slice(e), selStart: urlAt, selEnd: urlAt + 'https://'.length }
  })

  const code = () => {
    const ta = ref.current
    const sel = ta ? value.slice(ta.selectionStart, ta.selectionEnd) : ''
    if (sel.includes('\n')) wrap('```\n', '\n```', '')
    else wrap('`', '`', 'code')
  }

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (!(e.ctrlKey || e.metaKey)) return
    const k = e.key.toLowerCase()
    if (k === 'b') { e.preventDefault(); wrap('**', '**', 'bold text') }
    else if (k === 'i') { e.preventDefault(); wrap('_', '_', 'italic text') }
    else if (k === 'k') { e.preventDefault(); link(false) }
  }

  const run = (id: ToolId) => {
    switch (id) {
      case 'heading': return prefixLines(() => '## ')
      case 'bold': return wrap('**', '**', 'bold text')
      case 'italic': return wrap('_', '_', 'italic text')
      case 'bullets': return prefixLines(() => '- ')
      case 'numbers': return prefixLines((i) => `${i + 1}. `)
      case 'quote': return prefixLines(() => '> ')
      case 'code': return code()
      case 'codeblock': return insertBlock('```\ncommand or output\n```\n', 'command or output')
      case 'link': return link(false)
      case 'image': return onUpload ? imageInput.current?.click() : link(true)
      case 'file': return fileInput.current?.click()
      case 'table': return insertBlock('| Column | Column |\n| --- | --- |\n| Cell | Cell |\n', 'Column')
    }
  }

  const preview = (
    <div className="min-h-[420px] overflow-y-auto px-4 py-3">
      {value.trim()
        ? <MarkdownView source={value} base={base} />
        : <p className="text-sm text-muted">Nothing to preview yet.</p>}
    </div>
  )

  const modeSwitch = (
    <div className="inline-flex rounded-md border border-line p-0.5 text-xs font-medium">
      {([['rich', 'Editor', <Pencil key="r" size={13} />], ['write', 'Markdown', <FileText key="w" size={13} />], ['preview', 'Preview', <Eye key="p" size={13} />]] as const).map(([m, label, icon]) => (
        <button key={m} type="button" onClick={() => setMode(m)} aria-pressed={mode === m}
          className={cn('flex items-center gap-1 rounded px-2 py-1', mode === m ? 'bg-brand-600 text-white' : 'text-gray-700 hover:bg-gray-50')}>
          {icon} {label}
        </button>
      ))}
    </div>
  )

  return (
    <div className={cn('rounded-lg border bg-surface', invalid ? 'border-red-400' : dragging ? 'border-brand-500 ring-2 ring-brand-100' : 'border-line')}>
      {onUpload && (
        <>
          <input ref={imageInput} type="file" hidden multiple accept={IMAGE_TYPES} onChange={(e) => { void upload([...(e.target.files ?? [])]); e.target.value = '' }} />
          <input ref={fileInput} type="file" hidden multiple accept={FILE_TYPES} onChange={(e) => { void upload([...(e.target.files ?? [])]); e.target.value = '' }} />
        </>
      )}
      {mode === 'rich' ? (
        <RichTextEditor value={value} onChange={onChange} disabled={disabled} canUpload={!!onUpload}
          onFiles={(files) => void upload(files)} onPickImage={() => imageInput.current?.click()} onPickFile={() => fileInput.current?.click()}
          insertRef={richInsert} modeSwitch={modeSwitch} />
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-2 py-1.5">
            <div className="flex flex-wrap items-center gap-0.5">
              {TOOLS.filter((t) => t.id !== 'file' || onUpload).map((t) => (
                <button key={t.id} type="button" title={t.label} aria-label={t.label} onClick={() => run(t.id)}
                  disabled={disabled || mode === 'preview'}
                  className="rounded-md p-1.5 text-muted hover:bg-gray-100 hover:text-ink disabled:cursor-not-allowed disabled:opacity-40">
                  {t.icon}
                </button>
              ))}
            </div>
            {modeSwitch}
          </div>
          {mode === 'write' ? (
            <textarea
              ref={ref}
              value={value}
              onChange={(e) => onChange(e.target.value)}
              onKeyDown={onKeyDown}
              onPaste={(e) => {
                const files = [...e.clipboardData.files]
                if (onUpload && files.length) { e.preventDefault(); void upload(files) }
              }}
              onDragOver={(e) => { if (onUpload && e.dataTransfer.types.includes('Files')) { e.preventDefault(); setDragging(true) } }}
              onDragLeave={() => setDragging(false)}
              onDrop={(e) => {
                setDragging(false)
                const files = [...e.dataTransfer.files]
                if (onUpload && files.length) { e.preventDefault(); void upload(files) }
              }}
              disabled={disabled}
              maxLength={maxLength}
              placeholder={placeholder}
              spellCheck
              className="block min-h-[420px] w-full resize-y bg-transparent px-4 py-3 font-mono text-sm leading-6 text-ink placeholder:text-gray-400 focus:outline-none disabled:text-gray-500"
            />
          ) : preview}
        </>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-line px-3 py-1.5 text-xs text-muted">
        {uploading > 0
          ? <span className="font-medium text-brand-600">Uploading {uploading} file{uploading === 1 ? '' : 's'}…</span>
          : mode === 'rich'
            ? <span>{onUpload ? 'Paste or drop screenshots and files straight into the article · ' : ''}× takes an image or file out · Ctrl+Z undoes</span>
            : <span>Markdown: <code>**bold**</code> <code>_italic_</code> <code>## heading</code> <code>- list</code> <code>`code`</code> <code>[text](url)</code> · link another article with <code>/help/its-slug</code>{onUpload ? ' · paste or drop images to upload' : ''}</span>}
        <span className={cn(value.length > maxLength * 0.95 && 'text-red-600')}>{value.length.toLocaleString()} / {maxLength.toLocaleString()}</span>
      </div>
    </div>
  )
}
