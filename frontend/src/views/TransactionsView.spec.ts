import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { useLedgerStore } from '@/stores/ledger'
import type { Category, Ledger, PageResponse, Transaction } from '@/types'

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), categories: vi.fn(), update: vi.fn(), updateTags: vi.fn() }))
const route = reactive({ name: 'transactions', query: {} as Record<string, string> })
const push = vi.fn(async ({ query }: { query: Record<string, string> }) => { route.query = query })
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push }) }))
vi.mock('@/api', () => ({
  transactionsApi: { list: api.list, get: api.detail, update: api.update, updateTags: api.updateTags },
  categoriesApi: { list: api.categories },
  accountsApi: { options: vi.fn().mockResolvedValue([]) },
  tagsApi: { list: vi.fn().mockResolvedValue([]) }
}))
import TransactionsView from './TransactionsView.vue'

const stubs = {
  PageHeader: { template: '<div><slot name="actions" /></div>' },
  MessageBanner: { template: '<div><slot name="actions" /></div>' },
  UiModal: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
  CategorySelector: { template: '<div />' },
  TagSelector: { template: '<div />' },
  RecurringBillsModal: { template: '<div />' },
  TransactionFlow: { props: ['transactions'], emits: ['edit'], template: '<div class="test-flow"><span v-for="item in transactions" :key="item.id">{{ item.description }}</span><button @click="$emit(\'edit\', transactions[0])">编辑首笔</button></div>' }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function transaction(id: number, description = `账单${id}`): Transaction {
  return { id, amount: 28, type: 'EXPENSE', description, categoryId: 1, category: '餐饮', transactionDate: '2026-11-10', tags: [{ id: 7, name: '旅行', color: '#000', system: true }] } as Transaction
}
function page(description: string, number = 0): PageResponse<Transaction> {
  return { content: [transaction(number + 1, description)], number, size: 20, totalElements: 40, totalPages: 2, first: number === 0, last: number === 1 }
}
const categories: Category[] = [{ id: 1, name: '餐饮', level: 1, type: 'EXPENSE' }]
function mountView() { return mount(TransactionsView, { global: { stubs } }) }

enableAutoUnmount(afterEach)
beforeEach(() => {
  vi.clearAllMocks()
  route.name = 'transactions'
  route.query = {}
  api.list.mockReset().mockResolvedValue(page('原来的账单'))
  api.categories.mockReset().mockResolvedValue(categories)
  api.detail.mockReset().mockResolvedValue(transaction(1, '详情账单'))
  api.update.mockReset().mockResolvedValue(transaction(1))
  useLedgerStore().state.activeLedger = { id: 1, name: '测试账本', myRole: 'OWNER' } as Ledger
  window.matchMedia = vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })
})

describe('TransactionsView responsive data flow', () => {
  it('keeps the previous list visible and only applies the latest filter response', async () => {
    const wrapper = mountView()
    await flushPromises()
    const older = deferred<PageResponse<Transaction>>()
    const newer = deferred<PageResponse<Transaction>>()
    api.list.mockReturnValueOnce(older.promise).mockReturnValueOnce(newer.promise)
    route.query = { keyword: '旧查询' }
    await flushPromises()
    expect(wrapper.find('.test-flow').text()).toContain('原来的账单')
    expect(wrapper.text()).toContain('正在更新账单')
    route.query = { keyword: '最新查询' }
    await flushPromises()
    newer.resolve(page('新结果'))
    await flushPromises()
    older.resolve(page('过时结果'))
    await flushPromises()
    expect(wrapper.find('.test-flow').text()).toContain('新结果')
    expect(wrapper.text()).not.toContain('过时结果')
  })

  it('puts applied filters and pagination into the URL and hydrates browser navigation', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('#transaction-search').setValue('午餐')
    await wrapper.find('#transaction-search').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(push).toHaveBeenLastCalledWith({ name: 'transactions', query: { keyword: '午餐' } })
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '午餐', page: 0 }))
    const next = wrapper.findAll('button').find(button => button.text() === '下一页')!
    await next.trigger('click')
    await flushPromises()
    expect(push).toHaveBeenLastCalledWith({ name: 'transactions', query: { keyword: '午餐', page: '1' } })
    route.query = { keyword: '浏览器返回', page: '0', sort: 'asc' }
    await flushPromises()
    expect((wrapper.find('#transaction-search').element as HTMLInputElement).value).toBe('浏览器返回')
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '浏览器返回', page: 0, sort: 'transactionDate,asc' }))
  })

  it('shows a retry instead of presenting a failed initial request as an empty ledger', async () => {
    api.list.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('账单加载失败')
    expect(wrapper.text()).not.toContain('这段时间还没有账单')
    await wrapper.findAll('button').find(button => button.text() === '重新加载')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('原来的账单')
  })

  it('opens editing immediately, loads detail and categories together, and saves tags atomically', async () => {
    const wrapper = mountView()
    await flushPromises()
    const detail = deferred<Transaction>()
    const category = deferred<Category[]>()
    api.detail.mockReturnValueOnce(detail.promise)
    api.categories.mockReturnValueOnce(category.promise)
    await wrapper.find('.test-flow button').trigger('click')
    expect(wrapper.find('#edit-transaction-description').exists()).toBe(true)
    expect((wrapper.find('#edit-transaction-description').element as HTMLInputElement).value).toBe('原来的账单')
    expect(wrapper.find('fieldset').attributes('disabled')).toBeDefined()
    expect(api.detail).toHaveBeenCalledWith(1)
    expect(api.categories).toHaveBeenCalledTimes(2)
    category.resolve(categories)
    detail.resolve(transaction(1, '已核对的账单'))
    await flushPromises()
    expect(wrapper.find('fieldset').attributes('disabled')).toBeUndefined()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(api.update).toHaveBeenCalledWith(1, expect.objectContaining({ tagIds: [7], description: '已核对的账单' }))
    expect(api.updateTags).not.toHaveBeenCalled()
  })

  it('does not apply unsubmitted filter text during background refresh', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('#transaction-search').setValue('尚未查询')
    window.dispatchEvent(new Event('app:data-changed'))
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: undefined }))
    expect((wrapper.find('#transaction-search').element as HTMLInputElement).value).toBe('尚未查询')
  })
})
