import { Link } from 'react-router-dom'

const URL_RE = /https?:\/\/[^\s<>"']+/g

/**
 * Plain text with its web addresses made clickable — ticket comments and descriptions. Links to
 * this app (a Knowledge Base article sent in a reply) open in place; anything else in a new tab.
 * Text is never parsed as HTML; React escapes every piece.
 */
export function LinkifiedText({ text, className }: { text: string; className?: string }) {
  const parts: React.ReactNode[] = []
  let last = 0
  for (const m of text.matchAll(URL_RE)) {
    // Trailing punctuation usually ends the sentence, not the address.
    const url = m[0].replace(/[.,;:!?)\]]+$/, '')
    const start = m.index ?? 0
    if (start > last) parts.push(text.slice(last, start))
    parts.push(<AppLink key={start} url={url} />)
    last = start + url.length
  }
  if (last < text.length) parts.push(text.slice(last))
  return <p className={className}>{parts}</p>
}

function AppLink({ url }: { url: string }) {
  let internal: string | null = null
  try {
    const u = new URL(url)
    if (u.origin === window.location.origin) internal = u.pathname + u.search + u.hash
  } catch { /* not a URL after all — falls through to a plain link */ }
  const cls = 'break-all font-medium text-brand-600 underline underline-offset-2'
  return internal
    ? <Link to={internal} className={cls}>{url}</Link>
    : <a href={url} target="_blank" rel="noopener noreferrer" className={cls}>{url}</a>
}
