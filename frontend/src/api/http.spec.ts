import { afterEach, describe, expect, it, vi } from 'vitest'
import { authHeaders, clearSession, post, rawFetch, setActiveLedgerId, setUnauthorizedHandler, useRequestActivity } from './http'
import { rememberSession, storedSession, writeLocal, readLocal } from '@/utils/localState'

afterEach(() => {
  localStorage.clear()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  setUnauthorizedHandler(() => {})
  vi.unstubAllGlobals()
})

describe('请求账本上下文', () => {
  it('全局操作计数覆盖并发解析任务，并在成功和失败后归零', async () => {
    const finishes: Array<(response: Response) => void> = []
    const fetchMock = vi.fn(() => new Promise<Response>(resolve => { finishes.push(resolve) }))
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=token; path=/'
    rememberSession('alice')
    const { pendingOperations } = useRequestActivity()
    const first = post('/api/analysis/chat', { question: '本月支出' })
    const second = post('/api/transactions/parse-only', { text: '午餐28元' })
    const rejected = expect(second).rejects.toMatchObject({ status: 500 })
    expect(pendingOperations.value).toBe(2)
    await Promise.resolve()
    finishes[0]!(new Response(JSON.stringify({ success: true, data: {} })))
    await first
    expect(pendingOperations.value).toBe(1)
    finishes[1]!(new Response(JSON.stringify({ success: false, message: '解析失败', data: null }), { status: 500 }))
    await rejected
    expect(pendingOperations.value).toBe(0)
  })

  it.each(['json', 'raw'] as const)('同一用户名重新登录后忽略旧%s请求的401', async kind => {
    let finish!: (response: Response) => void
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(resolve => { finish = resolve })))
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    document.cookie = 'XSRF-TOKEN=token; path=/'
    rememberSession('alice')
    const pending = kind === 'json' ? post('/api/transactions/save', { amount: 35 }) : rawFetch('/api/transactions/export')
    const rejected = expect(pending).rejects.toMatchObject({ status: 401 })
    await Promise.resolve()
    clearSession()
    rememberSession('alice')
    setActiveLedgerId(1)
    finish(new Response(JSON.stringify({ success: false, message: '旧会话失效', data: null }), { status: 401 }))
    await rejected
    expect(unauthorized).not.toHaveBeenCalled()
    expect(storedSession()?.username).toBe('alice')
    expect(authHeaders().get('X-Ledger-Id')).toBe('1')
  })

  it.each(['json', 'raw'] as const)('等待CSRF时换用户，取消旧%s写入而不是使用新用户cookie提交', async kind => {
    let finishCsrf!: () => void
    const fetchMock = vi.fn().mockImplementationOnce(() => new Promise<Response>(resolve => {
      finishCsrf = () => {
        document.cookie = 'XSRF-TOKEN=new-token; path=/'
        resolve(new Response('{}'))
      }
    })).mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { id: 9 } })))
    vi.stubGlobal('fetch', fetchMock)
    rememberSession('alice')
    setActiveLedgerId(1)
    const pending = kind === 'json'
      ? post('/api/transactions/save', { amount: 35 })
      : rawFetch('/api/transactions/save', { method: 'POST', body: '{}' })
    const rejected = expect(pending).rejects.toBeInstanceOf(Error)
    clearSession()
    rememberSession('bob')
    setActiveLedgerId(2)
    finishCsrf()
    await rejected
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(storedSession()?.username).toBe('bob')
  })

  it.each(['json', 'raw'] as const)('旧用户的%s请求迟到401不会退出新用户', async kind => {
    let finish!: (response: Response) => void
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(resolve => { finish = resolve })))
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    document.cookie = 'XSRF-TOKEN=token; path=/'
    rememberSession('alice')
    setActiveLedgerId(1)
    const pending = kind === 'json' ? post('/api/transactions/save', { amount: 35 }) : rawFetch('/api/transactions/export')
    const rejected = expect(pending).rejects.toMatchObject({ status: 401 })
    // Mutating requests yield once while checking their CSRF cookie.
    await Promise.resolve()
    rememberSession('bob')
    setActiveLedgerId(2)
    writeLocal('draft:bob:2:test', { value: '新用户草稿' })
    finish(new Response(JSON.stringify({ success: false, message: '旧会话失效', data: null }), { status: 401 }))
    await rejected
    expect(unauthorized).not.toHaveBeenCalled()
    expect(storedSession()?.username).toBe('bob')
    expect(authHeaders().get('X-Ledger-Id')).toBe('2')
    expect(readLocal('draft:bob:2:test')).toEqual({ value: '新用户草稿' })
  })

  it('默认请求使用当前账本，明确指定的账本不会被覆盖', () => {
    setActiveLedgerId(2)
    expect(authHeaders().get('X-Ledger-Id')).toBe('2')
    expect(authHeaders({ 'X-Ledger-Id': '1' }).get('X-Ledger-Id')).toBe('1')
  })

  it('CSRF 获取期间切换账本不改变截图确认保存的目标账本', async () => {
    let finishCsrf!: () => void
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => new Promise<Response>(resolve => {
        finishCsrf = () => {
          document.cookie = 'XSRF-TOKEN=token; path=/'
          resolve(new Response('{}'))
        }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { id: 7 } })))
    vi.stubGlobal('fetch', fetchMock)
    setActiveLedgerId(1)
    const pending = post('/api/transactions/save', { amount: 35 }, { headers: { 'X-Ledger-Id': '1' } })
    setActiveLedgerId(2)
    finishCsrf()
    await pending
    const headers = fetchMock.mock.calls[1][1].headers as Headers
    expect(headers.get('X-Ledger-Id')).toBe('1')
    expect(headers.get('X-XSRF-TOKEN')).toBe('token')
  })
})
