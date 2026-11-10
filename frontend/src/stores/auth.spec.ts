import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Ledger } from '@/types'

const api = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn() }))
vi.mock('@/api', () => ({ authApi: api, ledgerApi: { list: vi.fn(), setDefault: vi.fn() } }))
import { useAuthStore } from './auth'
import { useLedgerStore } from './ledger'
import { ApiError } from '@/api/http'
import { readLocal, rememberSession, storedSession, writeLocal } from '@/utils/localState'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

beforeEach(() => {
  useAuthStore().forceLogout()
  localStorage.clear()
  api.me.mockReset()
  api.login.mockReset()
  api.register.mockReset()
  api.logout.mockReset().mockResolvedValue(undefined)
  useAuthStore().state.initialized = false
})

describe('登录状态的请求隔离', () => {
  it('旧的会话检查完成时，不能复活已退出的用户', async () => {
    const pending = deferred<{ username: string }>()
    api.me.mockReturnValueOnce(pending.promise)
    rememberSession('alice')
    const auth = useAuthStore()
    const restoring = auth.initialize()
    auth.forceLogout()
    pending.resolve({ username: 'alice' })
    await restoring
    expect(auth.isAuthenticated.value).toBe(false)
    expect(auth.state.username).toBe('')
    expect(auth.state.initialized).toBe(true)
    expect(storedSession()).toBeNull()
  })

  it.each(['success', 'unauthorized'] as const)('改登另一用户后忽略旧会话的迟到%s', async outcome => {
    const pending = deferred<{ username: string }>()
    api.me.mockReturnValueOnce(pending.promise)
    api.login.mockResolvedValue({ username: 'bob' })
    rememberSession('alice')
    const auth = useAuthStore()
    const restoring = auth.initialize()
    await auth.login('bob', 'password')
    if (outcome === 'success') pending.resolve({ username: 'alice' })
    else pending.reject(new ApiError('会话过期', 401))
    await restoring
    expect(auth.state.username).toBe('bob')
    expect(auth.isAuthenticated.value).toBe(true)
    expect(storedSession()?.username).toBe('bob')
  })

  it('旧检查的finally不会解除新检查的并发保护', async () => {
    const old = deferred<{ username: string }>()
    const current = deferred<{ username: string }>()
    api.me.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    api.login.mockResolvedValue({ username: 'bob' })
    const auth = useAuthStore()
    const first = auth.initialize()
    await auth.login('bob', 'password')
    const second = auth.initialize(true)
    old.resolve({ username: 'alice' })
    await first
    const joined = auth.initialize(true)
    expect(api.me).toHaveBeenCalledTimes(2)
    current.resolve({ username: 'bob' })
    await Promise.all([second, joined])
    expect(auth.state.username).toBe('bob')
  })

  it('离线只恢复原用户的本机身份，明确401则保持退出', async () => {
    const auth = useAuthStore()
    rememberSession('alice')
    api.me.mockRejectedValueOnce(new TypeError('offline'))
    await auth.initialize(true)
    expect(auth.state.username).toBe('alice')
    expect(auth.state.offlineSession).toBe(true)
    api.me.mockResolvedValueOnce(null)
    await auth.initialize(true)
    expect(auth.isAuthenticated.value).toBe(false)
    expect(auth.state.offlineSession).toBe(false)
    expect(auth.state.initialized).toBe(true)
  })

  it('服务端身份发生变化时清理旧用户的账本与本机草稿', async () => {
    rememberSession('alice')
    writeLocal('draft:alice:1:analysis-question', { value: { question: '旧用户草稿' } })
    useLedgerStore().state.activeLedger = { id: 1, name: 'Alice账本' } as Ledger
    useLedgerStore().state.initialized = true
    api.me.mockResolvedValueOnce({ username: 'bob' })
    await useAuthStore().initialize(true)
    expect(useAuthStore().state.username).toBe('bob')
    expect(useLedgerStore().state.activeLedger).toBeNull()
    expect(useLedgerStore().state.initialized).toBe(false)
    expect(readLocal('draft:alice:1:analysis-question')).toBeNull()
  })
})
