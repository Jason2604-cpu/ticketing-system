import type { KbAttachment, Ticket } from '../../types'

const clean = (s: string) => s.replace(/[[\]\n\r]/g, ' ').trim()

/** The Markdown that shows an uploaded file: an inline image, or a download link. */
export function attachmentMarkdown(a: Pick<KbAttachment, 'name' | 'url' | 'image'>): string {
  return a.image
    ? `![${clean(a.name.replace(/\.[^.]+$/, '')) || 'image'}](${a.url})`
    : `[${clean(a.name) || 'file'}](${a.url})`
}

/**
 * A first draft from a resolved ticket: its title, the problem as the customer described it, and
 * the last public reply from someone other than the customer — usually the fix.
 */
export function draftFromTicket(t: Ticket): { title: string; body: string } {
  const reply = [...t.comments].reverse()
    .find((c) => !c.internal && !c.system && c.author && c.author.id !== t.createdBy.id)
  return {
    title: t.title.slice(0, 200),
    body: `## Problem\n\n${t.description.trim()}\n\n## Fix\n\n${reply?.body.trim() ?? 'Describe the fix here.'}\n`,
  }
}

/** A plain-text line for a ticket reply that links an article (comments aren't Markdown). */
export function articleReplyLine(title: string, slug: string): string {
  return `${title}: ${window.location.origin}/help/${slug}`
}
