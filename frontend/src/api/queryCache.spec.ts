import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cachedQuery, clearQueryCache, expireQueries, invalidateQueries, queryKey } from './queryCache'
import { readLocal, rememberSession } from '@/utils/localState'

beforeEach(() => {
  localStorage.clear()
  rememberSession('alice')
  clearQueryCache()
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
})
afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })

describe('账本查询缓存', () => {
  it.each([[403, 'app:access-changed'], [404, 'app:data-changed']] as const)('后台收到明确%s后清除离线快照并通知重新验证', async (status, eventName) => {
    const key = queryKey('/api/transactions?size=20', '1')
    await cachedQuery(key, async () => [{ id: 9 }])
    expireQueries()
    const notified = new Promise<void>(resolve => window.addEventListener(eventName, () => resolve(), { once: true }))
    const denial = Object.assign(new Error('不再可用'), { status })
    // The last snapshot can render immediately, then definitive denial removes it.
    expect(await cachedQuery(key, async () => { throw denial })).toEqual([{ id: 9 }])
    await notified
    expect(readLocal(`query:${key}`)).toBeNull()
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    await expect(cachedQuery(key, async () => { throw new TypeError('offline') })).rejects.toThrow('offline')
  })

  it('重新连接只标过期，后续请求再断网仍保留本用户的最后成功快照', async () => {
    const key = queryKey('/api/statistics/summary', '1')
    await cachedQuery(key, async () => ({ amount: 28 }))
    rememberSession('bob')
    const otherKey = queryKey('/api/statistics/summary', '1')
    await cachedQuery(otherKey, async () => ({ amount: 99 }))
    const otherSnapshot = readLocal(`query:${otherKey}`)
    rememberSession('alice')
    expireQueries()
    expect(readLocal(`query:${key}`)).not.toBeNull()
    expect(readLocal(`query:${otherKey}`)).toEqual(otherSnapshot)
    const unavailable = vi.fn().mockRejectedValue(new TypeError('offline again'))
    expect(await cachedQuery(key, unavailable, { force: true })).toEqual({ amount: 28 })
    expect(unavailable).toHaveBeenCalledTimes(1)
    expect(readLocal(`query:${key}`)).not.toBeNull()
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    expect(await cachedQuery(key, unavailable)).toEqual({ amount: 28 })
    expect(unavailable).toHaveBeenCalledTimes(1)
  })

  it('合并并发请求，并隔离调用者对返回数组的修改', async () => {
    let resolve!: (value: { id: number }[]) => void
    const load = vi.fn(() => new Promise<{ id: number }[]>(done => { resolve = done }))
    const key = queryKey('/api/accounts/options', '1')
    const first = cachedQuery(key, load)
    const second = cachedQuery(key, load)
    resolve([{ id: 1 }])
    const a = await first
    a[0]!.id = 99
    expect(await second).toEqual([{ id: 1 }])
    expect(await cachedQuery(key, load)).toEqual([{ id: 1 }])
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('不同用户和账本不能读取对方的离线缓存', async () => {
    const key = queryKey('/api/transactions', '1')
    await cachedQuery(key, async () => [{ id: 9 }])
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const unavailable = vi.fn().mockRejectedValue(new TypeError('offline'))
    expect(await cachedQuery(key, unavailable)).toEqual([{ id: 9 }])
    await expect(cachedQuery(queryKey('/api/transactions', '2'), unavailable)).rejects.toThrow()
    rememberSession('bob')
    await expect(cachedQuery(queryKey('/api/transactions', '1'), unavailable)).rejects.toThrow()
  })

  it('写操作失效之后的迟到旧响应不会重新填回缓存', async () => {
    let resolve!: (value: string[]) => void
    const key = queryKey('/api/transactions', '1')
    const old = cachedQuery(key, () => new Promise<string[]>(done => { resolve = done }))
    invalidateQueries(['/api/transactions'], '1')
    resolve(['old'])
    await old
    const load = vi.fn().mockResolvedValue(['new'])
    expect(await cachedQuery(key, load)).toEqual(['new'])
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('过期内容立即返回，后台更新后下一次读取是新数据', async () => {
    vi.useFakeTimers()
    const key = queryKey('/api/statistics/summary', '1')
    await cachedQuery(key, async () => ({ amount: 10 }))
    vi.advanceTimersByTime(31000)
    let resolve!: (value: { amount: number }) => void
    const fresh = new Promise<{ amount: number }>(done => { resolve = done })
    expect(await cachedQuery(key, () => fresh)).toEqual({ amount: 10 })
    resolve({ amount: 20 })
    await fresh
    await Promise.resolve()
    expect(await cachedQuery(key, async () => ({ amount: 30 }))).toEqual({ amount: 20 })
  })
})
