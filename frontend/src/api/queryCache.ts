import { markCachedData } from '@/composables/useConnectivity'
import { readLocal, removeLocal, removeLocalMatching, userScope, writeLocal } from '@/utils/localState'

interface Entry { value: unknown; savedAt: number; key: string }
const memory = new Map<string, Entry>()
const inflight = new Map<string, Promise<unknown>>()
const MAX_ENTRIES = 60
const MAX_AGE = 7 * 86400000
let generation = 0

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

export function queryKey(path: string, ledger: string): string {
  const scope = path.startsWith('/api/ledgers') || path.startsWith('/api/categories') ? 'shared' : ledger || 'none'
  return `${userScope()}:${scope}:${path}`
}

function readEntry(key: string): Entry | null {
  const entry = memory.get(key) || readLocal<Entry>(`query:${key}`)
  if (!entry || Date.now() - entry.savedAt > MAX_AGE) return null
  memory.set(key, entry)
  return entry
}

function store(key: string, value: unknown): void {
  const entry: Entry = { key, value: clone(value), savedAt: Date.now() }
  memory.delete(key)
  memory.set(key, entry)
  while (memory.size > MAX_ENTRIES) memory.delete(memory.keys().next().value!)
  // Bound the local snapshot to recent, reasonably small responses.
  try {
    if (JSON.stringify(entry).length < 180000) writeLocal(`query:${key}`, entry)
    const keys = Object.keys(localStorage).filter(item => item.startsWith('accounting:v1:query:'))
    while (keys.length > MAX_ENTRIES) localStorage.removeItem(keys.shift()!)
  } catch { /* In-memory caching still works when persistent storage is full. */ }
}

export async function cachedQuery<T>(key: string, load: () => Promise<T>, options: { ttl?: number; force?: boolean } = {}): Promise<T> {
  const entry = readEntry(key)
  const cachedValue = () => { markCachedData(); return clone(entry!.value as T) }
  if (!navigator.onLine && entry) return cachedValue()
  if (!options.force && entry && Date.now() - entry.savedAt < (options.ttl ?? 30000)) return clone(entry.value as T)

  const fetchValue = (): Promise<T> => {
    const existing = inflight.get(key)
    if (existing) return existing.then(value => clone(value as T))
    const version = generation
    const promise = load().then(value => {
      if (version === generation) store(key, value)
      return value
    }).finally(() => { if (inflight.get(key) === promise) inflight.delete(key) })
    inflight.set(key, promise)
    return promise.then(value => clone(value))
  }

  if (entry && !options.force) {
    // Stale-while-revalidate: returning to a view never removes its content.
    void fetchValue().then(() => window.dispatchEvent(new Event('app:data-changed'))).catch(error => {
      const status = (error as { status?: number })?.status
      if (status && status >= 400 && status < 500) {
        memory.delete(key)
        removeLocal(`query:${key}`)
        window.dispatchEvent(new Event(status === 403 ? 'app:access-changed' : 'app:data-changed'))
      } else markCachedData()
    })
    return clone(entry.value as T)
  }
  try { return await fetchValue() }
  catch (error) {
    if (entry && (error instanceof TypeError || (error instanceof DOMException && error.name === 'TimeoutError'))) return cachedValue()
    throw error
  }
}

export function invalidateQueries(prefixes: string[] = [], ledger?: string): void {
  generation += 1
  const owner = `${userScope()}:`
  for (const [key] of memory) {
    if (!key.startsWith(owner)) continue
    if (ledger && !key.startsWith(`${owner}${ledger}:`) && !key.startsWith(`${owner}shared:`)) continue
    if (!prefixes.length || prefixes.some(prefix => key.includes(`:${prefix}`))) memory.delete(key)
  }
  try {
    for (const key of Object.keys(localStorage)) {
      if (!key.startsWith(`accounting:v1:query:${owner}`)) continue
      if (ledger && !key.includes(`query:${owner}${ledger}:`) && !key.includes(`query:${owner}shared:`)) continue
      if (!prefixes.length || prefixes.some(prefix => key.includes(`:${prefix}`))) localStorage.removeItem(key)
    }
  } catch { /* Storage is optional. */ }
  inflight.clear()
}

export function clearQueryCache(): void {
  generation += 1
  memory.clear()
  inflight.clear()
  removeLocalMatching(`query:${userScope()}:`)
}

/** Reconnect refreshes without deleting the last usable offline snapshot. */
export function expireQueries(): void {
  const prefix = `accounting:v1:query:${userScope()}:`
  const expire = (entry: Entry): Entry => ({ ...entry, savedAt: Math.min(entry.savedAt, Date.now() - 6 * 60000) })
  for (const [key, entry] of memory) {
    if (key.startsWith(`${userScope()}:`)) memory.set(key, expire(entry))
  }
  try {
    for (const key of Object.keys(localStorage).filter(key => key.startsWith(prefix))) {
      const entry = JSON.parse(localStorage.getItem(key)!) as Entry
      localStorage.setItem(key, JSON.stringify(expire(entry)))
    }
  } catch { /* Memory entries can still be refreshed. */ }
}
