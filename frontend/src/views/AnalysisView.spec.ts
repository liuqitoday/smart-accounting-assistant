import { flushPromises, mount, VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { ApiError } from '@/api/http'
import ResultCard from '@/components/analysis/ResultCard.vue'
import type { AnalysisMessage, PageResponse, Transaction } from '@/types'

const chat = vi.fn()
const messages = vi.fn()
const list = vi.fn()
const routerPush = vi.fn()
const routeState = reactive({ query: {} as Record<string, string | string[] | undefined> })

vi.mock('@/api', () => ({
  analysisApi: {
    chat: (...args: unknown[]) => chat(...args),
    messages: (...args: unknown[]) => messages(...args),
    clearMessages: vi.fn(),
  },
  transactionsApi: {
    list: (...args: unknown[]) => list(...args),
    get: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    restore: vi.fn(),
    updateTags: vi.fn(),
    export: vi.fn(),
    import: vi.fn(),
  },
  accountsApi: { list: vi.fn().mockResolvedValue([]) },
  categoriesApi: { list: vi.fn().mockResolvedValue([]) },
  tagsApi: { list: vi.fn().mockResolvedValue([]) },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => routeState,
}))

window.scrollTo = vi.fn()
if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    media: '',
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
    onchange: null,
  }) as unknown as typeof window.matchMedia
}

vi.mock('chart.js', () => {
  class Chart {
    static register(): void {}
    destroy(): void {}
  }
  return {
    Chart,
    ArcElement: {},
    CategoryScale: {},
    DoughnutController: {},
    Legend: {},
    LinearScale: {},
    LineController: {},
    LineElement: {},
    PointElement: {},
    Tooltip: {},
  }
})

import AnalysisView from './AnalysisView.vue'
import TransactionsView from './TransactionsView.vue'

function userMessage(content: string): AnalysisMessage {
  return { id: 1, role: 'USER', content, payload: null, status: 'OK', createdAt: '2026-08-25T12:00:00' }
}

function assistantMessageWithFollowUps(questions: string[]): AnalysisMessage {
  return {
    id: 2,
    role: 'ASSISTANT',
    content: '已完成分析',
    payload: JSON.stringify({
      schemaVersion: 2,
      rawPlan: {},
      normalizedPlan: {},
      results: [],
      status: 'OK',
      warnings: [],
      followUps: questions.map(question => ({ label: question, question })),
    }),
    status: 'OK',
    createdAt: '2026-08-25T12:00:01',
  }
}

function emptyPage(): PageResponse<Transaction> {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 20,
    number: 0,
    first: true,
    last: true,
  }
}

function emptyMessages(): PageResponse<AnalysisMessage> {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 20,
    number: 0,
    first: true,
    last: true,
  }
}

describe('AnalysisView', () => {
  beforeEach(() => {
    chat.mockReset()
    messages.mockReset()
    list.mockReset()
    routerPush.mockReset()
    routeState.query = {}
    messages.mockResolvedValue(emptyMessages())
    list.mockResolvedValue(emptyPage())
  })

  async function mountAnalysis(): Promise<VueWrapper> {
    const wrapper = mount(AnalysisView, {
      global: {
        stubs: {
          AppShell: { template: '<div><slot /></div>' },
          PageHeader: { template: '<div><slot name="actions" /></div>' },
          MessageBanner: { template: '<div />' },
          UiModal: { template: '<div />' },
        },
      },
    })
    await flushPromises()
    return wrapper
  }

  it('optimistically shows the question and exposes server follow-ups', async () => {
    chat.mockResolvedValue({
      userMessage: userMessage('本月支出'),
      assistantMessage: assistantMessageWithFollowUps(['查看明细']),
    })
    const wrapper = await mountAnalysis()
    await wrapper.find('textarea').setValue('本月支出')
    await wrapper.find('button.send').trigger('click')
    expect(wrapper.text()).toContain('本月支出')
    await flushPromises()
    expect(wrapper.text()).toContain('查看明细')
  })

  it('classifies rate-limit, plan, query and network failures with retry', async () => {
    chat.mockRejectedValueOnce(new ApiError('今日提问次数已达上限', 400, {
      success: false,
      message: '今日提问次数已达上限',
      data: null,
      errorCode: 'RATE_LIMITED',
    }))
    const wrapper = await mountAnalysis()
    await wrapper.vm.askQuestion('本月支出')
    await flushPromises()
    expect(wrapper.text()).toContain('达到分析次数上限')
    expect(wrapper.find('button.retry-button').exists()).toBe(true)
  })

  it('clicking transaction result navigates with period/type/category filters', async () => {
    chat.mockResolvedValue({
      userMessage: userMessage('本月支出'),
      assistantMessage: {
        id: 2,
        role: 'ASSISTANT',
        content: '已完成分析',
        payload: JSON.stringify({
          schemaVersion: 2,
          rawPlan: {},
          normalizedPlan: {},
          results: [{
            kind: 'TRANSACTIONS',
            title: '明细',
            metric: 'SUM',
            unit: 'CNY',
            transactionType: 'EXPENSE',
            period: { current: { start: '2026-08-01', end: '2026-08-25' } },
            filters: { categoryId: 3 },
            warnings: [],
            values: { totalCount: 1, displayedCount: 1, detailsOmitted: false },
            transactions: [{
              id: 9,
              amount: '12.00',
              type: 'EXPENSE',
              date: '2026-08-10',
              description: '咖啡',
              category: '餐饮',
              parentCategory: null,
              account: null,
              merchant: '咖啡',
            }],
          }],
          status: 'OK',
          warnings: [],
          followUps: [],
        }),
        status: 'OK',
        createdAt: '2026-08-25T12:00:01',
      },
    })
    const wrapper = await mountAnalysis()
    await wrapper.vm.askQuestion('本月支出')
    await flushPromises()
    await wrapper.findComponent(ResultCard).vm.$emit('open-transactions', {
      startDate: '2026-08-01',
      endDate: '2026-08-25',
      type: 'EXPENSE',
      categoryId: 3,
    })
    expect(routerPush).toHaveBeenCalledWith({
      name: 'transactions',
      query: {
        startDate: '2026-08-01',
        endDate: '2026-08-25',
        type: 'EXPENSE',
        categoryId: '3',
      },
    })
  })

  it('hydrates transaction filters from route query once and preserves back navigation', async () => {
    routeState.query = { startDate: '2026-08-01', endDate: '2026-08-25', type: 'EXPENSE' }
    mount(TransactionsView, {
      global: {
        stubs: {
          AppShell: { template: '<div><slot /></div>' },
          PageHeader: { template: '<div><slot name="actions" /></div>' },
          MessageBanner: { template: '<div />' },
          UiModal: { template: '<div />' },
          CategorySelector: { template: '<div />' },
          TagSelector: { template: '<div />' },
          TransactionFlow: { template: '<div />' },
          RecurringBillsModal: { template: '<div />' },
        },
      },
    })
    await flushPromises()
    expect(list).toHaveBeenCalledWith(expect.objectContaining({
      startDate: '2026-08-01',
      endDate: '2026-08-25',
      type: 'EXPENSE',
    }))
  })

  it('keeps an in-flight question when history arrives late and then merges it', async () => {
    let resolveHistory: ((value: unknown) => void) | undefined
    messages.mockReset()
    messages.mockImplementationOnce(() => new Promise(resolve => { resolveHistory = resolve }))
    let resolveChat: ((value: unknown) => void) | undefined
    chat.mockImplementationOnce(() => new Promise(resolve => { resolveChat = resolve }))

    const wrapper = mount(AnalysisView, {
      global: {
        stubs: {
          AppShell: { template: '<div><slot /></div>' },
          PageHeader: { template: '<div><slot name="actions" /></div>' },
          MessageBanner: { template: '<div />' },
          UiModal: { template: '<div />' },
        },
      },
    })
    await wrapper.vm.askQuestion('进行中的问题')
    expect(wrapper.text()).toContain('进行中的问题')

    resolveHistory?.({
      content: [{
        id: 5,
        role: 'USER',
        content: '更早的问题',
        payload: null,
        status: 'OK',
        createdAt: '2026-08-24T12:00:00',
      }],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0,
      first: true,
      last: true,
    })
    await flushPromises()
    expect(wrapper.text()).toContain('进行中的问题')
    expect(wrapper.text()).toContain('更早的问题')

    resolveChat?.({
      userMessage: {
        id: 6,
        role: 'USER',
        content: '进行中的问题',
        payload: null,
        status: 'OK',
        createdAt: '2026-08-25T12:00:00',
      },
      assistantMessage: assistantMessageWithFollowUps([]),
    })
    await flushPromises()
    expect(wrapper.text()).toContain('进行中的问题')
    expect(wrapper.text()).toContain('更早的问题')
  })

  it('ignores follow-up clicks while a question is in flight', async () => {
    let resolveChat: ((value: unknown) => void) | undefined
    chat.mockImplementationOnce(() => new Promise(resolve => { resolveChat = resolve }))
    const wrapper = await mountAnalysis()
    await wrapper.vm.askQuestion('本月支出')
    await wrapper.vm.askQuestion('查看明细')
    expect(chat).toHaveBeenCalledTimes(1)
    resolveChat?.({
      userMessage: userMessage('本月支出'),
      assistantMessage: assistantMessageWithFollowUps(['查看明细']),
    })
    await flushPromises()
  })
})
