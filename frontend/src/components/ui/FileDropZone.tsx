import { useEffect, useRef, useState, type DragEvent } from 'react'
import { FileText, Paperclip, X } from 'lucide-react'
import { cn } from '../../lib/cn'
import { formatBytes } from '../../lib/format'
import { ATTACHMENT_EXTENSIONS, MAX_ATTACHMENT_BYTES, MAX_ATTACHMENTS, describeExtensions, sameFile } from '../../lib/attachments'

/**
 * Drag-and-drop + browse picker for ticket attachments (Submit Ticket page, staff New Ticket modal).
 *
 * Not a <label> around the input, which is what the old Submit Ticket box was: a label has no drag
 * handlers, so a dropped file fell through to the browser, and inside a Field a label would also
 * hijack clicks. Files are APPENDED (picking again adds to the list), de-duplicated, and checked
 * against the backend's rules — and against the company's remaining storage when that is known —
 * before anything is uploaded. The picked files are listed under the box, each removable.
 */
export function FileDropZone({
  files, onChange, maxFiles = MAX_ATTACHMENTS, maxBytes = MAX_ATTACHMENT_BYTES, accept = ATTACHMENT_EXTENSIONS,
  storageRemainingBytes, disabled = false, className,
}: {
  files: File[]
  onChange: (files: File[]) => void
  maxFiles?: number
  maxBytes?: number
  /** Lower-case extensions with the dot, e.g. ".pdf". */
  accept?: string[]
  /** Bytes the company can still store. Files that would go over it are refused here. */
  storageRemainingBytes?: number
  disabled?: boolean
  className?: string
}) {
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [problems, setProblems] = useState<string[]>([])

  // A file dropped NEXT TO the box would otherwise make the browser leave the page to open it,
  // taking the half-written ticket with it. Only file drags are blocked; dragging text still works.
  useEffect(() => {
    const block = (e: globalThis.DragEvent) => { if (e.dataTransfer?.types.includes('Files')) e.preventDefault() }
    window.addEventListener('dragover', block)
    window.addEventListener('drop', block)
    return () => { window.removeEventListener('dragover', block); window.removeEventListener('drop', block) }
  }, [])

  const addFiles = (incoming: File[]) => {
    const next = [...files]
    const issues: string[] = []
    let used = files.reduce((n, f) => n + f.size, 0)
    for (const f of incoming) {
      const name = f.name.toLowerCase()
      if (!accept.some((ext) => name.endsWith(ext))) { issues.push(`${f.name}: only ${describeExtensions(accept)} files can be attached.`); continue }
      if (f.size > maxBytes) { issues.push(`${f.name}: larger than ${formatBytes(maxBytes)}.`); continue }
      if (next.some((g) => sameFile(f, g))) continue
      if (next.length >= maxFiles) { issues.push(`${f.name}: at most ${maxFiles} files per ticket.`); continue }
      if (storageRemainingBytes !== undefined && used + f.size > storageRemainingBytes) {
        issues.push(`${f.name}: would go over the company's storage limit (${formatBytes(Math.max(0, storageRemainingBytes - used))} free).`)
        continue
      }
      next.push(f)
      used += f.size
    }
    setProblems(issues)
    if (next.length !== files.length) onChange(next)
  }

  const openPicker = () => { if (!disabled) input.current?.click() }
  const onDragOver = (e: DragEvent<HTMLDivElement>) => {
    if (disabled) return
    e.preventDefault() // without this the browser never allows the drop
    e.dataTransfer.dropEffect = 'copy'
    if (!dragging) setDragging(true)
  }
  const onDragLeave = (e: DragEvent<HTMLDivElement>) => {
    // Moving onto a child element fires dragleave on the box too; only reset when the pointer really left.
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false)
  }
  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragging(false)
    if (!disabled) addFiles(Array.from(e.dataTransfer.files))
  }

  const storageFull = storageRemainingBytes !== undefined && storageRemainingBytes <= 0
  const storageLow = storageRemainingBytes !== undefined && !storageFull && storageRemainingBytes < maxBytes

  return (
    <div className={className}>
      <div
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-disabled={disabled}
        onClick={openPicker}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker() } }}
        onDragEnter={onDragOver}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
        className={cn(
          'flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-8 text-center transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-100',
          disabled ? 'cursor-not-allowed border-line opacity-60'
            : dragging ? 'cursor-copy border-brand-500 bg-brand-50' : 'cursor-pointer border-line hover:border-brand-500 hover:bg-brand-50/40',
        )}
      >
        <Paperclip size={20} className={dragging ? 'text-brand-600' : 'text-muted'} />
        <p className="mt-2 text-sm">{dragging ? 'Drop to attach' : <>Drag files here or <span className="font-medium text-brand-600">browse</span></>}</p>
        <p className="text-xs text-muted">{describeExtensions(accept)} — up to {formatBytes(maxBytes)} each, {maxFiles} files max</p>
      </div>
      {/* Outside the box on purpose: a click on the input would bubble back into the box's onClick. */}
      <input
        ref={input}
        type="file"
        multiple
        hidden
        accept={accept.join(',')}
        onChange={(e) => {
          const picked = Array.from(e.target.files ?? [])
          e.target.value = '' // so picking the same file again still fires onChange
          addFiles(picked)
        }}
      />

      {storageFull && <p className="mt-2 text-xs text-red-600">The company's storage is full — attachments can't be added. Contact support to upgrade the plan.</p>}
      {storageLow && <p className="mt-2 text-xs text-amber-700">Only {formatBytes(storageRemainingBytes)} of storage left on the company's plan.</p>}

      {files.length > 0 && (
        <ul className="mt-2 space-y-1.5">
          {files.map((f) => (
            <li key={`${f.name}|${f.size}|${f.lastModified}`} className="flex items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm">
              <FileText size={14} className="shrink-0 text-muted" />
              <span className="min-w-0 flex-1 truncate">{f.name}</span>
              <span className="shrink-0 text-xs text-muted">{formatBytes(f.size)}</span>
              <button
                type="button"
                disabled={disabled}
                onClick={() => { onChange(files.filter((g) => !sameFile(g, f))); setProblems([]) }}
                className="rounded-md p-1 text-muted hover:bg-red-50 hover:text-red-600"
                aria-label={`Remove ${f.name}`}
              >
                <X size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}
      {problems.length > 0 && (
        <ul className="mt-2 space-y-0.5 text-xs text-red-600">
          {problems.map((p, i) => <li key={`${i}:${p}`}>{p}</li>)}
        </ul>
      )}
    </div>
  )
}
