import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SaveTransactionRequest } from '@/types'

const mocks = vi.hoisted(() => ({ save: vi.fn(), initialize: vi.fn(), state: { offlineSession: false } }))
vi.mock('@/api', () => ({ transactionsApi: { save: mocks.save } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ initialize: mocks.initialize, state: mocks.state, isAuthenticated: { value: true } }) }))
import { saveOrQueueTransaction, useOutbox } from './outbox'
import { readLocal, rememberSession } from '@/utils/localState'
import { ApiError } from '@/api/http'

const payload: SaveTransactionRequest = {
  amount: 28, type: 'EXPENSE', description: '午餐', originalText: '午餐', categoryId: 1,
  transactionDate: '2026-11-10', tagIds: [2], clientRequestId: 'a2de1a84-43f3-41c1-9f7b-7b527537b152'
}
let user = 0
beforeEach(() => {
  localStorage.clear()
  rememberSession(`audit-${++user}`)
  localStorage.setItem('activeLedgerId', '1')
  mocks.save.mockReset()
  mocks.initialize.mockReset().mockResolvedValue(undefined)
  mocks.state.offlineSession = false
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
})

describe('可靠待同步账目', () => {
  it('断网提交保存到本机，恢复后使用原id和原账本同步', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    expect(await saveOrQueueTransaction(payload)).toEqual({ queued: true })
    expect(mocks.save).not.toHaveBeenCalled()
    localStorage.setItem('activeLedgerId', '2')
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    mocks.save.mockResolvedValue({ id: 10 })
    await useOutbox().sync()
    expect(mocks.save).toHaveBeenCalledWith(payload, 1)
    expect(useOutbox().entries.value).toHaveLength(0)
  })

  it('已发出但响应丢失时，重试保持同一幂等键', async () => {
    mocks.save.mockRejectedValueOnce(new TypeError('connection reset')).mockResolvedValue({ id: 10 })
    expect(await saveOrQueueTransaction(payload)).toEqual({ queued: true })
    await useOutbox().sync()
    expect(mocks.save.mock.calls.map(call => call[0].clientRequestId)).toEqual([payload.clientRequestId, payload.clientRequestId])
    expect(useOutbox().entries.value).toHaveLength(0)
  })

  it('明确的校验失败不排队，不能把失败提示成已保存', async () => {
    mocks.save.mockRejectedValue(new ApiError('分类不存在', 400))
    await expect(saveOrQueueTransaction(payload)).rejects.toThrow('分类不存在')
    expect(useOutbox().entries.value).toHaveLength(0)
  })

  it('切换用户不会替另一个用户同步待办', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    await saveOrQueueTransaction(payload)
    const oldUser = `audit-${user}`
    rememberSession('someone-else')
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    await useOutbox().sync()
    expect(mocks.save).not.toHaveBeenCalled()
    expect(readLocal<unknown[]>(`outbox:${oldUser}`)).toHaveLength(1)
  })
})
