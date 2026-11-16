import { Node as TiptapNode, type AnyExtension, type JSONContent } from '@tiptap/core'
import Image from '@tiptap/extension-image'
import { TaskItem, TaskList } from '@tiptap/extension-list'
import { TableKit } from '@tiptap/extension-table'
import { MarkdownManager } from '@tiptap/markdown'
import StarterKit from '@tiptap/starter-kit'
import { Marked } from 'marked'

/**
 * The rich-text side of the article editor: which formatting exists, and the Markdown ⇄ document
 * conversion. Articles are still STORED as Markdown — the version history diffs it, search indexes
 * it and readers get it through MarkdownView — the editor only shows it as formatted text.
 *
 * No React or browser APIs in this file, so the conversion can be exercised from a plain script.
 */

/** Where uploaded images and files live; a Markdown link to one is an attached file. */
export const MEDIA_PREFIX = '/api/media/'

const cleanName = (s: unknown) => String(s ?? '').replace(/[[\]\n\r]/g, ' ').trim() || 'file'

/**
 * An attached file. In Markdown it is an ordinary link, `[Notice.pdf](/api/media/<id>)`; in the
 * editor it is one solid chip, so it can't be half-deleted into a broken link.
 */
export const KbFile = TiptapNode.create({
  name: 'kbFile',
  group: 'inline',
  inline: true,
  atom: true,
  marks: '',
  selectable: true,
  draggable: true,

  addAttributes() {
    return { href: { default: '' }, name: { default: 'file' } }
  },
  parseHTML() {
    return [{
      tag: 'a[data-kb-file]',
      priority: 1100,
      getAttrs: (el) => ({ href: el.getAttribute('href') ?? '', name: el.textContent ?? 'file' }),
    }]
  },
  renderHTML({ node }) {
    return ['a', { 'data-kb-file': '', href: node.attrs.href }, String(node.attrs.name)]
  },
  renderText({ node }) {
    return String(node.attrs.name)
  },
  renderMarkdown: (node) => `[${cleanName(node.attrs?.name)}](${node.attrs?.href ?? ''})`,
})

/**
 * Everything the editor can hold — the same set react-markdown + remark-gfm show to readers:
 * headings, bold/italic/strikethrough, lists, task lists, quotes, code, links, images, tables,
 * rules. No underline or colours: Markdown has nowhere to keep them.
 *
 * The editor passes its own image/file nodes (the same ones plus how they're drawn).
 */
export function kbExtensions(image: typeof Image = Image, file: typeof KbFile = KbFile): AnyExtension[] {
  return [
    StarterKit.configure({
      underline: false,
      heading: { levels: [1, 2, 3, 4] },
      link: { openOnClick: false, autolink: true, HTMLAttributes: { rel: null, target: null } },
    }),
    // Inline, as in Markdown, so "text ![shot](…) text" survives. No data: URLs — a pasted
    // base64 screenshot would blow the article size limit; pasted images are uploaded instead.
    image.configure({ inline: true, allowBase64: false }),
    file,
    TableKit.configure({ table: { resizable: false } }),
    TaskList,
    TaskItem.configure({ nested: true }),
  ]
}

let manager: MarkdownManager | null = null
function markdown(): MarkdownManager {
  if (!manager) {
    // Readers see raw HTML as plain text (MarkdownView has no rehype-raw). Parse it the same way
    // here: with the HTML tokenizers off, "<b>x</b>" or "ping <ip>" stays the text that was typed
    // instead of being turned into formatting or dropped.
    const marked = new Marked()
    marked.use({ tokenizer: { html: () => undefined, tag: () => undefined } })
    manager = new MarkdownManager({ marked: marked as never, extensions: kbExtensions() })
  }
  return manager
}

/** Node types whose children are blocks — an image directly inside one needs a paragraph around it. */
const BLOCK_PARENTS = new Set(['doc', 'blockquote', 'listItem', 'taskItem', 'tableCell', 'tableHeader'])

/**
 * Two fix-ups after parsing:
 *  - links to uploaded files become file chips (several text runs of one link are one chip);
 *  - a paragraph holding nothing but an image is parsed as a bare image, which only suits block
 *    images — ours are inline, so it gets its paragraph back.
 */
function tidy(nodes: JSONContent[], parent: string): JSONContent[] {
  const out: JSONContent[] = []
  for (const n of nodes) {
    const href: unknown = n.type === 'text' ? n.marks?.find((m) => m.type === 'link')?.attrs?.href : undefined
    if (typeof href === 'string' && href.startsWith(MEDIA_PREFIX)) {
      const prev = out[out.length - 1]
      if (prev?.type === 'kbFile' && prev.attrs?.href === href) prev.attrs.name = `${prev.attrs.name}${n.text ?? ''}`
      else out.push({ type: 'kbFile', attrs: { href, name: n.text ?? 'file' } })
    } else if (n.type === 'image' && BLOCK_PARENTS.has(parent)) {
      out.push({ type: 'paragraph', content: [n] })
    } else {
      out.push(n.content ? { ...n, content: tidy(n.content, n.type ?? '') } : n)
    }
  }
  return out
}

/** Markdown → the editor's document. */
export function markdownToDoc(source: string): JSONContent {
  const doc = markdown().parse(source)
  const content = tidy(doc.content ?? [], 'doc')
  return { type: 'doc', content: content.length ? content : [{ type: 'paragraph' }] }
}

/** The inline pieces of a one-line snippet — what an upload returns (`![…](url)` / `[name](url)`). */
export function inlineFromMarkdown(snippet: string): JSONContent[] {
  return markdownToDoc(snippet).content?.[0]?.content ?? []
}

/** The editor's document → the Markdown that gets saved. */
export function docToMarkdown(doc: JSONContent): string {
  return markdown().serialize(doc).replace(/^\n+/, '').trimEnd()
}
