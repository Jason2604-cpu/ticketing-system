import { useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from 'react'
import { EditorContent, useEditor, useEditorState, type Editor } from '@tiptap/react'
import Image from '@tiptap/extension-image'
import { Placeholder } from '@tiptap/extensions'
import { TextSelection } from '@tiptap/pm/state'
import {
  Bold, Code, Heading2, Heading3, Image as ImageIcon, Italic, Link2, List, ListChecks, ListOrdered,
  Paperclip, Quote, Redo2, SquareCode, Strikethrough, Table, Undo2,
} from 'lucide-react'
import { api } from '../../lib/api'
import { cn } from '../../lib/cn'
import { KbFile, docToMarkdown, inlineFromMarkdown, kbExtensions, markdownToDoc } from './kbRichText'

/** Uploaded media is /api/media/<id>; the API may live on another origin in development. */
const mediaSrc = (src: string) => (src.startsWith('/api/') ? api.mediaUrl(src) ?? src : src)

const PAPERCLIP = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>'

/** The little × on an image or file. It takes the thing out of the article, not off the server. */
function removeButton(label: string, editor: Editor, getPos: () => number | undefined): HTMLButtonElement {
  const b = document.createElement('button')
  b.type = 'button'
  b.className = 'kb-rt-remove'
  b.title = label
  b.setAttribute('aria-label', label)
  b.textContent = '×'
  b.addEventListener('mousedown', (e) => e.preventDefault())
  b.addEventListener('click', (e) => {
    e.preventDefault()
    const pos = getPos()
    if (pos == null || !editor.isEditable) return
    editor.chain().focus().deleteRange({ from: pos, to: pos + 1 }).run()
  })
  return b
}
const onRemoveButton = (e: Event) => e.target instanceof Element && e.target.closest('.kb-rt-remove') != null

/** An image, drawn as the picture itself. */
const RichImage = Image.extend({
  addNodeView() {
    return ({ node, editor, getPos }) => {
      const dom = document.createElement('span')
      dom.className = 'kb-rt-image'
      const img = document.createElement('img')
      const paint = (attrs: Record<string, unknown>) => {
        const alt = typeof attrs.alt === 'string' ? attrs.alt : ''
        img.src = mediaSrc(typeof attrs.src === 'string' ? attrs.src : '')
        img.alt = alt
        img.title = alt
      }
      paint(node.attrs)
      dom.append(img, removeButton('Remove this image from the article', editor, getPos))
      return {
        dom,
        update: (next) => {
          if (next.type.name !== 'image') return false
          paint(next.attrs)
          return true
        },
        stopEvent: onRemoveButton,
        ignoreMutation: () => true,
      }
    }
  },
})

/** An attached file, drawn as one chip: paperclip, name, ×. */
const RichFile = KbFile.extend({
  addNodeView() {
    return ({ node, editor, getPos }) => {
      const dom = document.createElement('span')
      dom.className = 'kb-rt-file'
      const icon = document.createElement('span')
      icon.className = 'kb-rt-file-icon'
      icon.innerHTML = PAPERCLIP
      const name = document.createElement('span')
      name.className = 'kb-rt-file-name'
      const paint = (attrs: Record<string, unknown>) => {
        name.textContent = String(attrs.name ?? 'file')
        dom.title = `Attached file: ${name.textContent}`
      }
      paint(node.attrs)
      dom.append(icon, name, removeButton('Remove this file from the article', editor, getPos))
      return {
        dom,
        update: (next) => {
          if (next.type.name !== 'kbFile') return false
          paint(next.attrs)
          return true
        },
        stopEvent: onRemoveButton,
        ignoreMutation: () => true,
      }
    }
  },
})

/** Typed as "example.com" → https://example.com; app paths (/help/…), anchors and schemes stay. */
function normaliseUrl(raw: string): string | null {
  const url = raw.trim()
  if (!url) return ''
  if (/^(javascript|data|vbscript):/i.test(url)) return null
  if (/^(\/|#|[a-z][a-z0-9+.-]*:)/i.test(url)) return url
  return `https://${url}`
}

function Tool({ label, active, disabled, onClick, children }: { label: string; active?: boolean; disabled?: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" title={label} aria-label={label} aria-pressed={active} disabled={disabled}
      onMouseDown={(e) => e.preventDefault()} onClick={onClick}
      className={cn('rounded-md p-1.5 hover:bg-gray-100 hover:text-ink disabled:cursor-not-allowed disabled:opacity-40',
        active ? 'bg-brand-50 text-brand-700' : 'text-muted')}>
      {children}
    </button>
  )
}

const Divider = () => <span className="mx-1 h-5 w-px bg-line" aria-hidden="true" />

/**
 * The article body as formatted text: headings look like headings, an uploaded photo is the photo,
 * an attached file is a chip. Nothing here is fragile syntax — an image or file is one solid object
 * that is either there or not (× or Backspace removes it, Ctrl+Z brings it back).
 *
 * `value` / `onChange` are still Markdown (see kbRichText). Opening an article never rewrites it:
 * Markdown is only produced when the writer actually changes something.
 *
 * Uploading is the parent's job (it knows the article): pasted or dropped files go to `onFiles`,
 * the Image / Attach buttons to `onPickImage` / `onPickFile`, and a finished upload comes back
 * through `insertRef` as the Markdown snippet to place at the cursor.
 */
export function RichTextEditor({ value, onChange, disabled, canUpload, onFiles, onPickImage, onPickFile, insertRef, modeSwitch }: {
  value: string
  onChange: (markdown: string) => void
  disabled?: boolean
  canUpload: boolean
  onFiles: (files: File[]) => void
  onPickImage: () => void
  onPickFile: () => void
  insertRef: RefObject<((markdown: string) => void) | null>
  modeSwitch: ReactNode
}) {
  // What the editor last showed or produced, so our own onChange coming back as `value` isn't
  // mistaken for an outside change (which would reset the cursor and the undo history).
  const lastMarkdown = useRef(value)
  const onChangeRef = useRef(onChange)
  const onFilesRef = useRef(onFiles)
  const canUploadRef = useRef(canUpload)
  useEffect(() => { onChangeRef.current = onChange; onFilesRef.current = onFiles; canUploadRef.current = canUpload })

  const [linkUrl, setLinkUrl] = useState<string | null>(null)
  const openLinkRef = useRef<() => void>(() => {})

  const extensions = useMemo(() => [
    ...kbExtensions(RichImage, RichFile),
    Placeholder.configure({ placeholder: 'Describe the symptoms, then the fix. Paste or drop screenshots straight in.' }),
  ], [])

  const editor = useEditor({
    extensions,
    content: markdownToDoc(value),
    editable: !disabled,
    editorProps: {
      attributes: { class: 'kb-prose kb-rt-content', role: 'textbox', 'aria-multiline': 'true', 'aria-label': 'Article body' },
      handleKeyDown: (_view, e) => {
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); openLinkRef.current(); return true }
        return false
      },
      handlePaste: (_view, e) => {
        const files = [...(e.clipboardData?.files ?? [])]
        // Copying from Word or Excel also puts a picture of the selection on the clipboard;
        // when there is text, the text is what was meant.
        if (!canUploadRef.current || files.length === 0 || e.clipboardData?.getData('text/plain').trim()) return false
        e.preventDefault()
        onFilesRef.current(files)
        return true
      },
      handleDrop: (view, e, _slice, moved) => {
        const files = [...(e.dataTransfer?.files ?? [])]
        if (moved || !canUploadRef.current || files.length === 0) return false
        e.preventDefault()
        const at = view.posAtCoords({ left: e.clientX, top: e.clientY })
        if (at) view.dispatch(view.state.tr.setSelection(TextSelection.near(view.state.doc.resolve(at.pos))))
        onFilesRef.current(files)
        return true
      },
    },
    onUpdate: ({ editor: e }) => {
      const markdown = docToMarkdown(e.getJSON())
      lastMarkdown.current = markdown
      onChangeRef.current(markdown)
    },
  })

  // The body changed from outside: the article loaded or was saved, a version was restored, or a
  // file was inserted from the "Images & files" card.
  useEffect(() => {
    if (!editor || value.trim() === lastMarkdown.current.trim()) return
    lastMarkdown.current = value
    editor.commands.setContent(markdownToDoc(value), { emitUpdate: false })
  }, [editor, value])

  useEffect(() => { editor?.setEditable(!disabled, false) }, [editor, disabled])

  // A finished upload: into the empty line the cursor is on, otherwise on a line of its own below.
  useEffect(() => {
    insertRef.current = (markdown: string) => {
      if (!editor) return
      const inline = inlineFromMarkdown(markdown)
      if (inline.length === 0) return
      const { $from } = editor.state.selection
      const paragraph = { type: 'paragraph', content: inline }
      if ($from.parent.type.name === 'paragraph' && $from.parent.content.size === 0) editor.chain().focus().insertContent(inline).run()
      else if ($from.depth === 0) editor.chain().focus().insertContent(paragraph).run()
      else editor.chain().focus().insertContentAt($from.after(), paragraph).run()
    }
    return () => { insertRef.current = null }
  }, [editor, insertRef])

  const s = useEditorState({
    editor,
    selector: ({ editor: e }) => e && ({
      bold: e.isActive('bold'), italic: e.isActive('italic'), strike: e.isActive('strike'), code: e.isActive('code'),
      h2: e.isActive('heading', { level: 2 }), h3: e.isActive('heading', { level: 3 }),
      bullets: e.isActive('bulletList'), numbers: e.isActive('orderedList'), tasks: e.isActive('taskList'),
      quote: e.isActive('blockquote'), codeBlock: e.isActive('codeBlock'),
      link: e.isActive('link'), table: e.isActive('table'),
      canUndo: e.can().undo(), canRedo: e.can().redo(),
    }),
  })

  useEffect(() => {
    openLinkRef.current = () => { if (editor?.isEditable) setLinkUrl((editor.getAttributes('link').href as string | undefined) ?? '') }
  }, [editor])

  if (!editor || !s) return null

  const applyLink = () => {
    const url = normaliseUrl(linkUrl ?? '')
    if (url === null) return
    setLinkUrl(null)
    if (url === '') editor.chain().focus().extendMarkRange('link').unsetLink().run()
    else if (editor.state.selection.empty && !editor.isActive('link')) {
      editor.chain().focus().insertContent({ type: 'text', text: url, marks: [{ type: 'link', attrs: { href: url } }] }).run()
    } else editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
  }

  const off = disabled
  const run = () => editor.chain().focus()
  const tableButton = 'rounded-md border border-line px-2 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50'

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-2 py-1.5">
        <div className="flex flex-wrap items-center gap-0.5">
          <Tool label="Undo (Ctrl+Z)" disabled={off || !s.canUndo} onClick={() => run().undo().run()}><Undo2 size={15} /></Tool>
          <Tool label="Redo (Ctrl+Y)" disabled={off || !s.canRedo} onClick={() => run().redo().run()}><Redo2 size={15} /></Tool>
          <Divider />
          <Tool label="Heading" active={s.h2} disabled={off} onClick={() => run().toggleHeading({ level: 2 }).run()}><Heading2 size={15} /></Tool>
          <Tool label="Sub-heading" active={s.h3} disabled={off} onClick={() => run().toggleHeading({ level: 3 }).run()}><Heading3 size={15} /></Tool>
          <Tool label="Bold (Ctrl+B)" active={s.bold} disabled={off} onClick={() => run().toggleBold().run()}><Bold size={15} /></Tool>
          <Tool label="Italic (Ctrl+I)" active={s.italic} disabled={off} onClick={() => run().toggleItalic().run()}><Italic size={15} /></Tool>
          <Tool label="Strikethrough" active={s.strike} disabled={off} onClick={() => run().toggleStrike().run()}><Strikethrough size={15} /></Tool>
          <Divider />
          <Tool label="Bulleted list" active={s.bullets} disabled={off} onClick={() => run().toggleBulletList().run()}><List size={15} /></Tool>
          <Tool label="Numbered list" active={s.numbers} disabled={off} onClick={() => run().toggleOrderedList().run()}><ListOrdered size={15} /></Tool>
          <Tool label="Checklist" active={s.tasks} disabled={off} onClick={() => run().toggleTaskList().run()}><ListChecks size={15} /></Tool>
          <Tool label="Quote" active={s.quote} disabled={off} onClick={() => run().toggleBlockquote().run()}><Quote size={15} /></Tool>
          <Tool label="Code" active={s.code} disabled={off} onClick={() => run().toggleCode().run()}><Code size={15} /></Tool>
          <Tool label="Code block (commands, output)" active={s.codeBlock} disabled={off} onClick={() => run().toggleCodeBlock().run()}><SquareCode size={15} /></Tool>
          <Divider />
          <Tool label="Link (Ctrl+K)" active={s.link} disabled={off} onClick={() => openLinkRef.current()}><Link2 size={15} /></Tool>
          {canUpload && <Tool label="Image" disabled={off} onClick={onPickImage}><ImageIcon size={15} /></Tool>}
          {canUpload && <Tool label="Attach a file" disabled={off} onClick={onPickFile}><Paperclip size={15} /></Tool>}
          <Tool label="Table" active={s.table} disabled={off || s.table}
            onClick={() => run().insertTable({ rows: 3, cols: 2, withHeaderRow: true }).run()}><Table size={15} /></Tool>
        </div>
        {modeSwitch}
      </div>

      {linkUrl !== null && (
        <form className="flex flex-wrap items-center gap-2 border-b border-line bg-gray-50 px-3 py-2"
          onSubmit={(e) => { e.preventDefault(); applyLink() }}>
          <input autoFocus value={linkUrl} onChange={(e) => setLinkUrl(e.target.value)} aria-label="Link address"
            onKeyDown={(e) => { if (e.key === 'Escape') { setLinkUrl(null); editor.commands.focus() } }}
            placeholder="https://…  or  /help/article-slug to link another article"
            className="min-w-0 flex-1 rounded-md border border-line bg-surface px-2.5 py-1.5 text-sm text-ink placeholder:text-gray-400 focus:border-brand-500 focus:outline-none" />
          <button type="submit" className="rounded-md bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700">{s.link ? 'Update link' : 'Add link'}</button>
          {s.link && (
            <button type="button" className={tableButton}
              onClick={() => { setLinkUrl(null); run().extendMarkRange('link').unsetLink().run() }}>Remove link</button>
          )}
          <button type="button" className={tableButton} onClick={() => { setLinkUrl(null); editor.commands.focus() }}>Cancel</button>
        </form>
      )}

      {s.table && !off && (
        <div className="flex flex-wrap items-center gap-1.5 border-b border-line bg-gray-50 px-3 py-1.5">
          <span className="mr-1 text-xs text-muted">Table:</span>
          <button type="button" className={tableButton} onMouseDown={(e) => e.preventDefault()} onClick={() => run().addRowAfter().run()}>Add row</button>
          <button type="button" className={tableButton} onMouseDown={(e) => e.preventDefault()} onClick={() => run().addColumnAfter().run()}>Add column</button>
          <button type="button" className={tableButton} onMouseDown={(e) => e.preventDefault()} onClick={() => run().deleteRow().run()}>Delete row</button>
          <button type="button" className={tableButton} onMouseDown={(e) => e.preventDefault()} onClick={() => run().deleteColumn().run()}>Delete column</button>
          <button type="button" className={cn(tableButton, 'text-red-600 hover:bg-red-50')} onMouseDown={(e) => e.preventDefault()} onClick={() => run().deleteTable().run()}>Delete table</button>
        </div>
      )}

      <EditorContent editor={editor} className="kb-rt" />
    </>
  )
}
