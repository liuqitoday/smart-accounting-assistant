import type { ApiResponse } from '@/types'
import { readonly, ref } from 'vue'
import { cachedQuery, clearQueryCache, invalidateQueries, queryKey } from '@/api/queryCache'
import { advanceSessionEpoch, currentSessionEpoch, clearUserLocalData, userScope } from '@/utils/localState'
import { setConnectionAvailable } from '@/composables/useConnectivity'

export { invalidateQueries } from '@/api/queryCache'

const LEDGER_KEY = 'activeLedgerId'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

let unauthorizedHandler: (() => void) | null = null
const pendingOperations = ref(0)
export function useRequestActivity() { return { pendingOperations: readonly(pendingOperations) } }

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
  advanceSessionEpoch()
  clearQueryCache()
  clearUserLocalData()
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

let csrfRequest: Promise<void> | null = null
export async function ensureCsrfCookie(): Promise<void> {
  if (readCookie(CSRF_COOKIE)) return
  if (csrfRequest) return csrfRequest
  csrfRequest = timedFetch('/api/auth/csrf', {
    method: 'GET',
    credentials: 'same-origin',
  }).then(response => {
    if (!response.ok) throw new ApiError('连接失败，请稍后重试', response.status)
  }).finally(() => { csrfRequest = null })
  return csrfRequest
}

export function authHeaders(extra?: HeadersInit, method = 'GET'): Headers {
  const headers = new Headers(extra)
  const ledgerId = getActiveLedgerId()
  if (ledgerId && !headers.has('X-Ledger-Id')) {
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

export async function get<T>(path: string, params?: Record<string, unknown>, options: { force?: boolean; signal?: AbortSignal } = {}): Promise<T> {
  const url = `${path}${params ? queryString(params) : ''}`
  const headers = authHeaders()
  const load = () => executeRequest<T>(url, { headers, signal: options.signal })
  if (options.signal || path.includes('/auth/') || path.includes('/members')) return load()
  const ttl = path === '/api/categories' || path === '/api/transactions/capabilities' ? 5 * 60000 : 30000
  return cachedQuery(queryKey(url, headers.get('X-Ledger-Id') || ''), load, { ttl, force: options.force })
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
  const owner = userScope()
  const epoch = currentSessionEpoch()
  const headers = authHeaders(init.headers, method)
  if (isMutating(method)) {
    await ensureCsrfCookie()
  }

  if (owner !== userScope() || epoch !== currentSessionEpoch()) throw new ApiError('登录状态已变更，请在当前账号下重试', 409)
  if (isMutating(method)) headers.set(CSRF_HEADER, readCookie(CSRF_COOKIE))
  const response = await timedFetch(normalizePath(path), {
    ...init,
    credentials: 'same-origin',
    headers,
  })

  if (response.status === 401) {
    if (owner !== userScope() || epoch !== currentSessionEpoch()) throw new ApiError('此请求所属的登录状态已变更', 401)
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
  const track = isMutating(init.method || 'GET')
  if (track) pendingOperations.value += 1
  try { return await runRequest<T>(path, init, options) }
  finally { if (track) pendingOperations.value -= 1 }
}

async function runRequest<T>(
  path: string,
  init: RequestInit = {},
  options: { silentUnauthorized?: boolean } = {},
): Promise<T> {
  const method = init.method || 'GET'
  // Capture the ledger before any CSRF round trip. Switching ledger while an
  // operation is pending must not redirect the write to the new ledger.
  const headers = authHeaders(init.headers, method)
  const owner = userScope()
  const epoch = currentSessionEpoch()
  if (isMutating(method)) {
    await ensureCsrfCookie()
    headers.set(CSRF_HEADER, readCookie(CSRF_COOKIE))
  }

  if (owner !== userScope() || epoch !== currentSessionEpoch()) throw new ApiError('登录状态已变更，请在当前账号下重试', 409)
  const response = await timedFetch(normalizePath(path), {
    ...init,
    credentials: 'same-origin',
    headers,
  }, /\/parse-|\/analysis\/chat/.test(path) ? 70000 : 20000)

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (response.status === 401) {
    if (owner !== userScope() || epoch !== currentSessionEpoch()) throw new ApiError('此请求所属的登录状态已变更', 401)
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

  if (isMutating(method) && !/\/auth\/|\/parse-|\/analysis\/chat/.test(path) && owner === userScope() && epoch === currentSessionEpoch()) {
    const prefixes = path.startsWith('/api/transactions') || path.startsWith('/api/transfers') || path.startsWith('/api/recurring-bills')
      ? ['/api/transactions', '/api/statistics', '/api/accounts', '/api/analysis', '/api/recurring-bills']
      : path.startsWith('/api/accounts') ? ['/api/accounts', '/api/statistics']
        : path.startsWith('/api/tags') ? ['/api/tags', '/api/transactions', '/api/recurring-bills']
          : []
    invalidateQueries(prefixes, headers.get('X-Ledger-Id') || undefined)
    window.dispatchEvent(new Event('app:data-changed'))
  }

  return payload.data
}

async function timedFetch(path: string, init: RequestInit, timeout = 15000): Promise<Response> {
  const controller = new AbortController()
  const abort = () => controller.abort(init.signal?.reason)
  if (init.signal?.aborted) abort()
  else init.signal?.addEventListener('abort', abort, { once: true })
  let timedOut = false
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, timeout)
  try {
    const response = await fetch(path, { ...init, signal: controller.signal })
    setConnectionAvailable(true)
    return response
  } catch (error) {
    if (timedOut) throw new DOMException('请求超时，请重试；已填写的内容会保留', 'TimeoutError')
    if (error instanceof TypeError) setConnectionAvailable(false)
    throw error
  } finally {
    clearTimeout(timer)
    init.signal?.removeEventListener('abort', abort)
  }
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
