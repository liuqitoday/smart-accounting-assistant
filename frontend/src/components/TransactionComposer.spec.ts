import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TransactionComposer from './TransactionComposer.vue'
import { useLedgerStore } from '@/stores/ledger'
import { setConnectionAvailable } from '@/composables/useConnectivity'
import type { AccountOption, Ledger, TransactionParseResponse } from '@/types'

const { parseOnly, submit } = vi.hoisted(() => ({ parseOnly: vi.fn(), submit: vi.fn() }))
vi.mock('@/api', () => ({
  transactionsApi: { parseOnly }, categoriesApi: { list: vi.fn().mockResolvedValue([{ id: 10, name: '餐饮', level: 1, type: 'EXPENSE' }]) }
}))
vi.mock('@/stores/outbox', () => ({ saveOrQueueTransaction: submit }))
const mounted: ReturnType<typeof mount>[] = []
beforeEach(() => {
  localStorage.clear()
  localStorage.setItem('activeLedgerId', '1')
  useLedgerStore().state.activeLedger = { id: 1, myRole: 'OWNER' } as Ledger
  setConnectionAvailable(true)
  parseOnly.mockReset().mockResolvedValue(parsed())
  submit.mockReset().mockResolvedValue({ queued: false, transaction: { id: 10 } })
})
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()))
function render(accounts: AccountOption[] = []) {
  const wrapper = mount(TransactionComposer, {
    props: { accounts, tags: [], active: true },
    global: { stubs: { CategorySelector: true, TagSelector: true } }
  })
  mounted.push(wrapper)
  return wrapper
}
async function parse(wrapper: ReturnType<typeof render>) {
  await wrapper.get('#transactionText').setValue('午饭 25 元')
  await wrapper.findAll('button').find(button => button.text().includes('解析这笔账'))!.trigger('click')
  await flushPromises()
}
function parsed(accountId: number | null = null): TransactionParseResponse {
  return { amount: 25, type: 'EXPENSE', description: '午饭', originalText: '午饭25元', categoryId: 10, transactionDate: '2026-11-10', accountId }
}
function account(id: number, overrides: Partial<AccountOption> = {}): AccountOption {
  return { id, name: `账户${id}`, type: 'CASH', active: true, ...overrides }
}

describe('文字解析草稿与异步体验', () => {
  it('AI未提供账户时使用启用的默认账户，已提供的账户不覆盖', async () => {
    const wrapper = render([account(1), account(2, { default: true })])
    await parse(wrapper)
    expect((wrapper.get('#parsed-account').element as HTMLSelectElement).value).toBe('2')
    parseOnly.mockResolvedValueOnce(parsed(1))
    await wrapper.findAll('button').find(button => button.text().includes('重新解析'))!.trigger('click')
    await flushPromises()
    expect((wrapper.get('#parsed-account').element as HTMLSelectElement).value).toBe('1')
  })
  it('无默认或默认账户停用时保持未指定', async () => {
    const wrapper = render([account(1), account(2, { default: true, active: false })])
    await parse(wrapper)
    expect((wrapper.get('#parsed-account').element as HTMLSelectElement).value).toBe('')
  })
  it('切换记账方式不取消解析，迟到结果继续保存在草稿', async () => {
    let resolve!: (result: TransactionParseResponse) => void
    parseOnly.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const wrapper = render()
    await parse(wrapper)
    const signal = parseOnly.mock.calls[0][1] as AbortSignal
    await wrapper.setProps({ active: false })
    expect(signal.aborted).toBe(false)
    resolve(parsed())
    await flushPromises()
    expect(wrapper.find('#parsed-amount').exists()).toBe(true)
  })
  it('取消请求保留输入并忽略迟到结果', async () => {
    let resolve!: (result: TransactionParseResponse) => void
    parseOnly.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const wrapper = render()
    await parse(wrapper)
    const signal = parseOnly.mock.calls[0][1] as AbortSignal
    await wrapper.findAll('button').find(button => button.text() === '取消解析')!.trigger('click')
    resolve(parsed())
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.find('#parsed-amount').exists()).toBe(false)
    expect((wrapper.get('#transactionText').element as HTMLTextAreaElement).value).toBe('午饭 25 元')
  })
  it('重建组件恢复输入和结果，标签与交易一次保存，失败重试复用key', async () => {
    submit.mockRejectedValue(new Error('保存暂不可用'))
    let wrapper = render()
    await parse(wrapper)
    wrapper.unmount()
    wrapper = render()
    await flushPromises()
    expect((wrapper.get('#transactionText').element as HTMLTextAreaElement).value).toBe('午饭 25 元')
    expect(wrapper.find('#parsed-amount').exists()).toBe(true)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(submit.mock.calls[0][0]).toMatchObject({ tagIds: [], clientRequestId: expect.any(String) })
    expect(submit.mock.calls[1][0].clientRequestId).toBe(submit.mock.calls[0][0].clientRequestId)
    expect(wrapper.find('#parsed-amount').exists()).toBe(true)
  })
  it('离线禁用AI解析，但已确认结果仍可排队', async () => {
    const wrapper = render()
    await parse(wrapper)
    setConnectionAvailable(false)
    submit.mockResolvedValue({ queued: true })
    await flushPromises()
    expect(wrapper.findAll('button').find(button => button.text().includes('重新解析'))!.attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.emitted('saved')).toEqual([[{ queued: true }]])
    expect((wrapper.get('#transactionText').element as HTMLTextAreaElement).value).toBe('')
  })
})
