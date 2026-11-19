/**
 * Ticket attachment rules, mirrored from the backend (app-config.properties):
 *   app.media.allowed-attachment-types  image/jpeg, image/png, image/webp, application/pdf, text/csv, text/plain
 *   app.media.max-attachment-size-mb    20
 *   app.media.max-attachments-per-ticket 10
 * The backend still enforces all of them; checking here just means a bad file is refused
 * before the ticket is created, instead of in a toast afterwards.
 */
export const ATTACHMENT_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.webp', '.pdf', '.csv', '.txt']
export const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
export const MAX_ATTACHMENTS = 10

/** "PNG, JPG, WebP, PDF, CSV or TXT" — the hint text, built from the same list the check uses. */
export function describeExtensions(exts: string[] = ATTACHMENT_EXTENSIONS): string {
  const names = [...new Set(exts.map((e) => (e === '.jpeg' ? '.jpg' : e)))]
    .map((e) => (e === '.webp' ? 'WebP' : e.slice(1).toUpperCase()))
  return names.length > 1 ? `${names.slice(0, -1).join(', ')} or ${names[names.length - 1]}` : names[0] ?? ''
}

/** Same file picked twice (from the drop and from browse, say) — compared without reading it. */
export const sameFile = (a: File, b: File) => a.name === b.name && a.size === b.size && a.lastModified === b.lastModified

/** Bytes a company can still store, from its subscription; undefined when unknown. */
export function storageRemaining(sub?: { limits: { storageLimitMb: number }; usage: { storageUsedBytes: number } }): number | undefined {
  if (!sub) return undefined
  return Math.max(0, sub.limits.storageLimitMb * 1024 * 1024 - sub.usage.storageUsedBytes)
}
