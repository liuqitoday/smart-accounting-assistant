import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TransactionComposer from './TransactionComposer.vue'
import { useLedgerStore } from '@/stores/ledger'
import { setConnectionAvailable } from '@/composables/useConnectivity'
import type { AccountOption, Ledger, TransactionParseResponse } from '@/types'

const { parseOnly, submit, listCategories } = vi.hoisted(() => ({ parseOnly: vi.fn(), submit: vi.fn(), listCategories: vi.fn() }))
vi.mock('@/api', () => ({
  transactionsApi: { parseOnly }, categoriesApi: { list: listCategories }
}))
vi.mock('@/stores/outbox', () => ({ saveOrQueueTransaction: submit }))
const mounted: ReturnType<typeof mount>[] = []
beforeEach(() => {
  localStorage.clear()
  localStorage.setItem('activeLedgerId', '1')
  useLedgerStore().state.activeLedger = { id: 1, myRole: 'OWNER' } as Ledger
  setConnectionAvailable(true)
  parseOnly.mockReset().mockResolvedValue(parsed())
  listCategories.mockReset().mockResolvedValue([{ id: 10, name: '餐饮', level: 1, type: 'EXPENSE' }])
  submit.mockReset().mockResolvedValue({ queued: false, transaction: { id: 10 } })
})
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()))
function render(accounts: AccountOption[] = [], attached = false) {
  const wrapper = mount(TransactionComposer, {
    attachTo: attached ? document.body : undefined,
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
  it('解析后收起原文，修改和收起保留核对结果，重新解析才应用新原文', async () => {
    const wrapper = render([], true)
    await parse(wrapper)
    expect(wrapper.get('#transactionText').isVisible()).toBe(false)
    expect(wrapper.get('.composer-source-summary').text()).toContain('午饭 25 元')
    expect(wrapper.find('.composer-source-hint').exists()).toBe(false)
    await wrapper.get('#parsed-amount').setValue('26')
    await wrapper.get('.composer-source-summary button').trigger('click')
    expect(wrapper.get('#transactionText').isVisible()).toBe(true)
    await wrapper.get('#transactionText').setValue('晚饭 30 元')
    await wrapper.get('.composer-source-summary button').trigger('click')
    expect(wrapper.get('.composer-source-hint').text()).toContain('重新解析')
    expect((wrapper.get('#parsed-amount').element as HTMLInputElement).value).toBe('26')
    await wrapper.get('.composer-source-summary button').trigger('click')
    parseOnly.mockResolvedValueOnce({ ...parsed(), amount: 30, description: '晚饭' })
    await wrapper.findAll('button').find(button => button.text().includes('重新解析'))!.trigger('click')
    await flushPromises()
    expect(parseOnly).toHaveBeenLastCalledWith('晚饭 30 元', expect.any(AbortSignal))
    expect(wrapper.get('#transactionText').isVisible()).toBe(false)
    expect(wrapper.find('.composer-source-hint').exists()).toBe(false)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(submit.mock.calls[0][0]).toMatchObject({ amount: 30, originalText: '晚饭 30 元' })
  })
  it('重新解析时禁止保存旧结果，取消和失败都保留编辑区及已核对的字段', async () => {
    const wrapper = render()
    await parse(wrapper)
    await wrapper.get('.composer-source-summary button').trigger('click')
    await wrapper.get('#transactionText').setValue('晚饭 30 元')
    let resolve!: (result: TransactionParseResponse) => void
    parseOnly.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    await wrapper.findAll('button').find(button => button.text().includes('重新解析'))!.trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(submit).not.toHaveBeenCalled()
    expect(wrapper.get('.record-save-actions button').attributes('disabled')).toBeDefined()
    await wrapper.findAll('button').find(button => button.text() === '取消解析')!.trigger('click')
    resolve({ ...parsed(), amount: 30 }); await flushPromises()
    expect(wrapper.get('#transactionText').isVisible()).toBe(true)
    expect((wrapper.get('#parsed-amount').element as HTMLInputElement).value).toBe('25')
    parseOnly.mockRejectedValueOnce(new Error('识别暂不可用'))
    await wrapper.findAll('button').find(button => button.text().includes('重新解析'))!.trigger('click')
    await flushPromises()
    expect(wrapper.get('#transactionText').isVisible()).toBe(true)
    expect(wrapper.text()).toContain('识别暂不可用')
    expect((wrapper.get('#transactionText').element as HTMLTextAreaElement).value).toBe('晚饭 30 元')
  })
  it('分类加载完且按钮启用后再恢复焦点，后台解析完成不抢焦点', async () => {
    let finishCategories!: (value: unknown[]) => void
    listCategories.mockReturnValue(new Promise(done => { finishCategories = done }))
    const wrapper = render([], true)
    await parse(wrapper)
    expect(wrapper.get('.composer-source-summary button').attributes('disabled')).toBeDefined()
    finishCategories([]); await flushPromises()
    expect(document.activeElement).toBe(wrapper.get('.composer-source-summary button').element)
    await wrapper.get('.composer-source-summary button').trigger('click')
    let finishParse!: (result: TransactionParseResponse) => void
    parseOnly.mockImplementationOnce(() => new Promise(done => { finishParse = done }))
    await wrapper.findAll('button').find(button => button.text().includes('重新解析'))!.trigger('click')
    await wrapper.setProps({ active: false })
    const elsewhere = document.createElement('button')
    document.body.append(elsewhere); elsewhere.focus()
    finishParse(parsed()); await flushPromises()
    expect(document.activeElement).toBe(elsewhere)
    elsewhere.remove()
  })
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
