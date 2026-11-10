import { afterEach, describe, expect, it, vi } from 'vitest'
import { authHeaders, post, setActiveLedgerId } from './http'

afterEach(() => {
  localStorage.clear()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  vi.unstubAllGlobals()
})

describe('请求账本上下文', () => {
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
