import { computed, ref } from 'vue'
import { transactionsApi } from '@/api'
import { ApiError, getActiveLedgerId } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { readLocal, storedSession, userScope, writeLocal } from '@/utils/localState'
import type { SaveTransactionRequest, TransactionParseResponse } from '@/types'

interface PendingTransaction {
  id: string
  ledgerId: number
  payload: SaveTransactionRequest
  createdAt: number
  error?: string
}

const entries = ref<PendingTransaction[]>([])
const syncing = ref(false)
let scope = ''
const sending = new Map<string, Promise<TransactionParseResponse>>()

function load(): void {
  const nextScope = userScope()
  if (nextScope === scope) return
  scope = nextScope
  entries.value = readLocal<PendingTransaction[]>(`outbox:${scope}`) || []
}

function persist(owner: string, values: PendingTransaction[]): void {
  if (!writeLocal(`outbox:${owner}`, values)) throw new Error('本机存储空间不足，请保留草稿并联网后重试')
  if (scope === owner) entries.value = values
}

function remove(owner: string, id: string): void {
  const values = readLocal<PendingTransaction[]>(`outbox:${owner}`) || []
  persist(owner, values.filter(entry => entry.id !== id))
}

function send(entry: PendingTransaction): Promise<TransactionParseResponse> {
  const key = `${userScope()}:${entry.ledgerId}:${entry.id}`
  const existing = sending.get(key)
  if (existing) return existing
  const promise = transactionsApi.save(entry.payload, entry.ledgerId).finally(() => sending.delete(key))
  sending.set(key, promise)
  return promise
}

function networkFailure(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof DOMException && error.name === 'TimeoutError')
}

/** Durable before sending: a lost response or browser exit can safely replay. */
export async function saveOrQueueTransaction(payload: SaveTransactionRequest, ledgerId = Number(getActiveLedgerId())):
Promise<{ queued: boolean; transaction?: TransactionParseResponse }> {
  load()
  if (!storedSession() || !ledgerId) throw new Error('请先选择账本')
  const owner = scope
  const id = payload.clientRequestId || crypto.randomUUID()
  const entry: PendingTransaction = { id, ledgerId, payload: { ...payload, clientRequestId: id }, createdAt: Date.now() }
  const values = readLocal<PendingTransaction[]>(`outbox:${owner}`) || []
  const previous = values.find(item => item.id === id && item.ledgerId === ledgerId)
  if (previous && JSON.stringify(previous.payload) !== JSON.stringify(entry.payload)) throw new Error('这笔账正在同步，请先完成同步再修改')
  if (!previous) {
    if (values.length >= 100) throw new Error('待同步账目已满，请联网同步后再记账')
    persist(owner, [...values, entry])
  }
  if (!navigator.onLine || useAuthStore().state.offlineSession) return { queued: true }
  try {
    const transaction = await send(entry)
    remove(owner, id)
    return { queued: false, transaction }
  } catch (error) {
    if (networkFailure(error)) return { queued: true }
    remove(owner, id)
    throw error
  }
}

export function useOutbox() {
  load()
  return {
    entries: computed(() => { load(); return entries.value }),
    syncing,
    reload: load,
    async sync(retryErrors = false): Promise<void> {
      load()
      if (syncing.value || !navigator.onLine || !storedSession() || !entries.value.length) return
      syncing.value = true
      const owner = scope
      try {
        const auth = useAuthStore()
        await auth.initialize(true)
        if (!auth.isAuthenticated.value || auth.state.offlineSession || owner !== userScope()) return
        for (const entry of [...entries.value]) {
          if (owner !== userScope() || !navigator.onLine) break
          if (entry.error && !retryErrors) continue
          try {
            await send(entry)
            remove(owner, entry.id)
          } catch (error) {
            if (networkFailure(error) || (error instanceof ApiError && error.status === 401)) break
            const values = readLocal<PendingTransaction[]>(`outbox:${owner}`) || []
            persist(owner, values.map(item => item.id === entry.id ? {
              ...item, error: error instanceof Error ? error.message : '同步失败，请重试'
            } : item))
          }
        }
      } finally { syncing.value = false }
    },
    discard(id: string): void { load(); remove(scope, id) }
  }
}
