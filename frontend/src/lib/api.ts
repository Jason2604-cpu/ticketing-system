/**
 * Minimal typed fetch wrapper for the backend.
 * - Always sends the HttpOnly auth cookie (credentials: 'include').
 * - Normalises the backend's error shape { timestamp, status, error, fields? } into ApiError.
 */
const BASE = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(/\/$/, '')

export class ApiError extends Error {
  status: number
  fields?: Record<string, string>
  constructor(status: number, message: string, fields?: Record<string, string>) {
    super(message)
    this.status = status
    this.fields = fields
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

async function request<T>(method: Method, path: string, body?: unknown, init?: RequestInit): Promise<T> {
  const isForm = body instanceof FormData
  const res = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers: isForm ? undefined : body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
    ...init,
  })

  if (res.status === 204) return undefined as T

  const text = await res.text()
  let data: unknown = undefined
  try { data = text ? JSON.parse(text) : undefined } catch { data = text }

  if (!res.ok) {
    const err = (data ?? {}) as { error?: string; fields?: Record<string, string> }
    throw new ApiError(res.status, err.error ?? res.statusText ?? 'Request failed', err.fields)
  }
  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
  /** Absolute URL for an authenticated media file (img src works because the cookie is sent). */
  mediaUrl: (relative: string | null | undefined) => (relative ? `${BASE}${relative.replace(/^\/api/, '')}` : undefined),
  /**
   * Absolute URL for an API path, for links the BROWSER fetches rather than this client —
   * a CSV download, where letting the browser navigate keeps the Content-Disposition filename
   * that a blob built in JS would lose.
   */
  absoluteUrl: (path: string) => `${BASE}${path}`,
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError
export const errorMessage = (e: unknown) =>
  isApiError(e) ? e.message : e instanceof Error ? e.message : 'Something went wrong'
