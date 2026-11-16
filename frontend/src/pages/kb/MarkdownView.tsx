import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Link } from 'react-router-dom'
import { api } from '../../lib/api'
import type { KbBase } from './kbAccess'

/**
 * An article body. Markdown + GitHub extras (tables, task lists, strikethrough) through
 * react-markdown, with NO raw-HTML plugin: an article containing <script> or <img onerror> shows it
 * as text, and react-markdown's URL filter drops javascript: links. That is the whole XSS story,
 * so don't add rehype-raw here.
 *
 * Links between articles are written as /help/<slug> (what a customer can open). On the staff side
 * they're rewritten to /kb/<slug> and routed in-app; other app paths route in-app too; anything
 * external opens in a new tab.
 */
export function MarkdownView({ source, base, className }: { source: string; base: KbBase; className?: string }) {
  const components: Components = {
    a({ href, children }) {
      const url = href ?? ''
      const article = url.match(/^\/(?:help|kb)\/(.+)$/)
      if (article) return <Link to={`${base}/${article[1]}`}>{children}</Link>
      // An attached file (/api/media/<id>): served by the API, which checks the reader may have it.
      if (url.startsWith('/api/')) return <a href={api.mediaUrl(url)} target="_blank" rel="noopener noreferrer">{children}</a>
      if (url.startsWith('/') && !url.startsWith('//')) return <Link to={url}>{children}</Link>
      if (url.startsWith('#')) return <a href={url}>{children}</a>
      return <a href={url} target="_blank" rel="noopener noreferrer">{children}</a>
    },
    img({ src, alt }) {
      const raw = typeof src === 'string' ? src : ''
      // Uploaded images are /api/media/<id>; the API may live on another origin in development.
      const url = raw.startsWith('/api/') ? api.mediaUrl(raw) : raw
      return url ? <img src={url} alt={alt ?? ''} loading="lazy" /> : null
    },
  }
  return (
    <div className={`kb-prose ${className ?? ''}`}>
      <Markdown remarkPlugins={[remarkGfm]} components={components}>{source}</Markdown>
    </div>
  )
}
