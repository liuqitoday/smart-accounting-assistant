import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Ledger } from '@/types'
const api = vi.hoisted(() => ({ list: vi.fn(), setDefault: vi.fn() }))
vi.mock('@/api', () => ({ ledgerApi: api }))
import { useLedgerStore } from './ledger'
import { getActiveLedgerId } from '@/api/http'
import { rememberSession } from '@/utils/localState'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function ledger(id: number, name: string): Ledger { return { id, name, myRole: 'OWNER', default: true } as Ledger }
beforeEach(() => {
  useLedgerStore().reset()
  localStorage.clear()
  rememberSession('alice')
  api.list.mockReset()
  api.setDefault.mockReset()
})

describe('账本加载的用户和生命周期隔离', () => {
  it('退出并改登后独立加载，迟到结果不能写入新用户账本', async () => {
    const old = deferred<Ledger[]>()
    const current = deferred<Ledger[]>()
    api.list.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    const store = useLedgerStore()
    const first = store.load()
    store.reset()
    rememberSession('bob')
    const second = store.load()
    expect(api.list).toHaveBeenCalledTimes(2)
    old.resolve([ledger(1, 'Alice账本')])
    expect(await first).toBeNull()
    expect(store.state.ledgers).toEqual([])
    expect(store.state.loading).toBe(true)
    expect(store.state.initialized).toBe(false)
    const joined = store.load()
    expect(api.list).toHaveBeenCalledTimes(2)
    current.resolve([ledger(2, 'Bob账本')])
    await Promise.all([second, joined])
    expect(store.state.activeLedger?.name).toBe('Bob账本')
    expect(getActiveLedgerId()).toBe('2')
    expect(store.state.loading).toBe(false)
  })

  it('旧用户加载失败不会覆盖新用户的成功状态', async () => {
    const old = deferred<Ledger[]>()
    api.list.mockReturnValueOnce(old.promise).mockResolvedValueOnce([ledger(2, 'Bob账本')])
    const store = useLedgerStore()
    const first = store.load()
    const rejected = expect(first).rejects.toThrow('旧请求断网')
    store.reset()
    rememberSession('bob')
    await store.load()
    old.reject(new Error('旧请求断网'))
    await rejected
    expect(store.state.activeLedger?.id).toBe(2)
    expect(store.state.initialized).toBe(true)
    expect(store.state.loadError).toBe('')
  })

  it('首次失败不会被判定为没有账本，重试成功后清除错误', async () => {
    const store = useLedgerStore()
    api.list.mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce([ledger(1, '个人账本')])
    await expect(store.load()).rejects.toThrow('offline')
    expect(store.state.initialized).toBe(false)
    expect(store.state.loadError).toContain('重新连接')
    await store.load(true)
    expect(api.list).toHaveBeenLastCalledWith(true)
    expect(store.state.initialized).toBe(true)
    expect(store.state.loadError).toBe('')
    expect(store.state.activeLedger?.id).toBe(1)
  })

  it('无效账本选择不会改变请求头上下文', async () => {
    api.list.mockResolvedValueOnce([ledger(1, '个人账本')])
    const store = useLedgerStore()
    await store.load()
    store.switchTo(999)
    expect(store.state.activeLedger?.id).toBe(1)
    expect(getActiveLedgerId()).toBe('1')
  })
})
