import type { ApiResponse } from '@/types'

const LEDGER_KEY = 'activeLedgerId'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

let unauthorizedHandler: (() => void) | null = null

export class ApiError extends Error {
  status?: number
  response?: ApiResponse<unknown>

  constructor(message: string, status?: number, response?: ApiResponse<unknown>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.response = response
  }
}

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export function getActiveLedgerId(): string {
  return localStorage.getItem(LEDGER_KEY) || ''
}

export function setActiveLedgerId(id: number | string | null | undefined): void {
  if (id === null || id === undefined || id === '') {
    localStorage.removeItem(LEDGER_KEY)
    return
  }
  localStorage.setItem(LEDGER_KEY, String(id))
}

export function clearSession(): void {
  localStorage.removeItem(LEDGER_KEY)
}

export function queryString(params: Record<string, unknown>): string {
  const search = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value)) {
      value.forEach(item => {
        if (item !== undefined && item !== null && item !== '') {
          search.append(key, String(item))
        }
      })
      return
    }
    search.set(key, String(value))
  })

  const serialized = search.toString()
  return serialized ? `?${serialized}` : ''
}

export async function ensureCsrfCookie(): Promise<void> {
  if (readCookie(CSRF_COOKIE)) return
  await fetch('/api/auth/csrf', {
    method: 'GET',
    credentials: 'same-origin',
  })
}

export function authHeaders(extra?: HeadersInit, method = 'GET'): Headers {
  const headers = new Headers(extra)
  const ledgerId = getActiveLedgerId()
  if (ledgerId) {
    headers.set('X-Ledger-Id', ledgerId)
  }

  if (isMutating(method)) {
    const csrfToken = readCookie(CSRF_COOKIE)
    if (csrfToken) {
      headers.set(CSRF_HEADER, csrfToken)
    }
  }
  return headers
}

export async function get<T>(path: string, params?: Record<string, unknown>): Promise<T> {
  return executeRequest<T>(`${path}${params ? queryString(params) : ''}`)
}

export async function getOptional<T>(path: string, params?: Record<string, unknown>): Promise<T | null> {
  return executeRequest<T | null>(`${path}${params ? queryString(params) : ''}`, {}, { silentUnauthorized: true })
}

export async function post<T>(path: string, body?: unknown, init: RequestInit = {}): Promise<T> {
  const method = 'POST'
  const headers = body instanceof FormData
    ? init.headers
    : { 'Content-Type': 'application/json', ...headersToObject(init.headers) }

  return executeRequest<T>(path, {
    ...init,
    method,
    body: body instanceof FormData ? body : JSON.stringify(body ?? {}),
    headers,
  })
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return executeRequest<T>(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  })
}

export function del<T>(path: string): Promise<T> {
  return executeRequest<T>(path, { method: 'DELETE' })
}

export async function rawFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = init.method || 'GET'
  if (isMutating(method)) {
    await ensureCsrfCookie()
  }

  const response = await fetch(normalizePath(path), {
    ...init,
    credentials: 'same-origin',
    headers: authHeaders(init.headers, method),
  })

  if (response.status === 401) {
    handleUnauthorized()
  }

  if (!response.ok) {
    throw new ApiError(`请求失败（${response.status}）`, response.status)
  }

  return response
}

async function executeRequest<T>(
  path: string,
  init: RequestInit = {},
  options: { silentUnauthorized?: boolean } = {},
): Promise<T> {
  const method = init.method || 'GET'
  if (isMutating(method)) {
    await ensureCsrfCookie()
  }

  const response = await fetch(normalizePath(path), {
    ...init,
    credentials: 'same-origin',
    headers: authHeaders(init.headers, method),
  })

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (response.status === 401) {
    if (options.silentUnauthorized) {
      return null as T
    }
    handleUnauthorized(payload)
  }

  if (!response.ok) {
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, response.status, payload ?? undefined)
  }

  if (!payload) throw new ApiError('服务端返回为空', response.status)
  if (!payload.success) throw new ApiError(payload.message || '操作失败', response.status, payload)

  return payload.data
}

function handleUnauthorized(payload?: ApiResponse<unknown> | null): never {
  clearSession()
  unauthorizedHandler?.()
  throw new ApiError(payload?.message || '登录已过期，请重新登录', 401, payload ?? undefined)
}

function normalizePath(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  return path.startsWith('/') ? path : `/${path}`
}

function isMutating(method: string): boolean {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())
}

function readCookie(name: string): string {
  const escapedName = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = document.cookie.match(new RegExp(`(?:^|; )${escapedName}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : ''
}

function headersToObject(headers?: HeadersInit): Record<string, string> {
  if (!headers) return {}
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries())
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers)
  }
  return headers
}
