import { createElement } from 'react'
import { Archive, FileEdit, Globe, Lock, Send } from 'lucide-react'
import { GenericBadge } from '../../components/ui/Badge'
import type { KbCompanyRef, KbSnippetPart, KbStatus, KbVisibility } from '../../types'
import { kbIcon } from './kbIcons'

export function CategoryIcon({ name, size = 16, className }: { name?: string | null; size?: number; className?: string }) {
  return createElement(kbIcon(name), { size, className })
}

/** Internal = staff only (grey lock); Public = customers can read it too (green globe). */
export function VisibilityBadge({ visibility }: { visibility?: KbVisibility | null }) {
  if (!visibility) return null
  return visibility === 'PUBLIC'
    ? <GenericBadge tone="green"><Globe size={11} className="mr-1" />Public</GenericBadge>
    : <GenericBadge tone="gray"><Lock size={11} className="mr-1" />Internal</GenericBadge>
}

export function StatusBadge({ status, version }: { status: KbStatus; version?: number | null }) {
  if (status === 'DRAFT') return <GenericBadge tone="gray"><FileEdit size={11} className="mr-1" />Draft</GenericBadge>
  if (status === 'ARCHIVED') return <GenericBadge tone="red"><Archive size={11} className="mr-1" />Archived</GenericBadge>
  return <GenericBadge tone="blue">Published{version ? ` · v${version}` : ''}</GenericBadge>
}

export function RequestedBadge() {
  return <GenericBadge tone="purple"><Send size={11} className="mr-1" />Publish requested</GenericBadge>
}

export function ChangesBadge() {
  return <GenericBadge tone="amber">Unpublished changes</GenericBadge>
}

/** "All companies", or the first two names and a count. */
export function ScopeBadges({ companies, max = 2 }: { companies?: KbCompanyRef[] | null; max?: number }) {
  const list = companies ?? []
  if (list.length === 0) return <GenericBadge tone="gray">All companies</GenericBadge>
  return (
    <span className="inline-flex flex-wrap gap-1">
      {list.slice(0, max).map((c) => <GenericBadge key={c.id} tone="gray">{c.name}</GenericBadge>)}
      {list.length > max && <GenericBadge tone="gray">+{list.length - max} more</GenericBadge>}
    </span>
  )
}

/**
 * A search excerpt. The API sends plain-text pieces with the matches flagged, so highlighting is
 * just <mark> around text React escapes — article text is never rendered as HTML.
 */
export function SnippetText({ parts, className }: { parts?: KbSnippetPart[] | null; className?: string }) {
  if (!parts?.length) return null
  return (
    <p className={className ?? 'line-clamp-2 text-sm text-muted'}>
      {parts.map((p, i) => p.match
        ? <mark key={i} className="rounded bg-yellow-100 px-0.5 text-ink">{p.text}</mark>
        : <span key={i}>{p.text}</span>)}
    </p>
  )
}
