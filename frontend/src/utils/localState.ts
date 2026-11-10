const PREFIX = 'accounting:v1:'
const SESSION_KEY = `${PREFIX}session`
const MAX_AGE = 7 * 24 * 60 * 60 * 1000
let sessionEpoch = 0

export function currentSessionEpoch(): number { return sessionEpoch }
export function advanceSessionEpoch(): void { sessionEpoch += 1 }

export function readLocal<T>(key: string): T | null {
  try {
    const value = localStorage.getItem(`${PREFIX}${key}`)
    return value ? JSON.parse(value) as T : null
  } catch {
    return null
  }
}

export function writeLocal(key: string, value: unknown): boolean {
  try {
    localStorage.setItem(`${PREFIX}${key}`, JSON.stringify(value))
    return true
  } catch {
    return false
  }
}

export function removeLocal(key: string): void {
  try { localStorage.removeItem(`${PREFIX}${key}`) } catch { /* Storage may be unavailable. */ }
}

export function removeLocalMatching(prefix: string): void {
  try {
    Object.keys(localStorage).filter(key => key.startsWith(`${PREFIX}${prefix}`)).forEach(key => localStorage.removeItem(key))
  } catch { /* Private browsing can disable storage. */ }
}

export function storedSession(): { username: string; savedAt: number } | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const value = JSON.parse(raw)
    return typeof value.username === 'string' && value.username && Date.now() - value.savedAt < MAX_AGE ? value : null
  } catch {
    return null
  }
}

export function rememberSession(username: string): void {
  writeLocal('session', { username, savedAt: Date.now() })
}

export function userScope(): string {
  return encodeURIComponent(storedSession()?.username || 'anonymous')
}

export function ledgerScope(): string {
  return `${userScope()}:${localStorage.getItem('activeLedgerId') || 'none'}`
}

export function clearUserLocalData(): void {
  const user = userScope()
  removeLocalMatching(`query:${user}:`)
  removeLocalMatching(`draft:${user}:`)
  removeLocal('session')
  // Pending submissions are retained under their owner until that same user
  // signs in again. Expiring a session must not silently discard a saved draft.
}
