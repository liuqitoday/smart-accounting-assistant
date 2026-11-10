import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ summary: vi.fn(), byCategory: vi.fn(), dailyTrend: vi.fn(), trend: vi.fn(), topExpenses: vi.fn() }))
const charts = vi.hoisted(() => ({ created: vi.fn(), updated: vi.fn(), destroyed: vi.fn() }))
vi.mock('@/api', () => ({ statisticsApi: api }))
vi.mock('chart.js', () => {
  class Chart {
    static register(): void {}
    canvas: unknown
    data: { labels: unknown[]; datasets: { data: unknown[]; backgroundColor?: unknown }[] }
    constructor(canvas: unknown, config: { data: Chart['data'] }) { this.canvas = canvas; this.data = config.data; charts.created(config) }
    destroy() { charts.destroyed() }
    update(mode: string) { charts.updated(mode) }
    resize(): void {}
  }
  return { Chart, ArcElement: {}, BarController: {}, BarElement: {}, CategoryScale: {}, DoughnutController: {}, Legend: {}, LinearScale: {}, LineController: {}, LineElement: {}, PointElement: {}, Tooltip: {} }
})
import StatisticsView from './StatisticsView.vue'
const stubs = {
  PageHeader: { template: '<div />' },
  RouterLink: { template: '<a><slot /></a>' },
  MessageBanner: { template: '<div />' },
  AnimatedNumber: { props: ['value'], template: '<span>{{ value }}</span>' },
  StatDelta: { template: '<span />' }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const summary = { totalIncome: 1000, totalExpense: 200, balance: 800, transactionCount: 3 }
const category = [{ categoryId: 1, categoryName: '餐饮', amount: 200, percentage: 100, transactionCount: 3 }]
const trend = [{ month: '2026-11-10', income: 1000, expense: 200, balance: 800 }]
const expenses = [{ id: 1, amount: 88, description: '晚餐', transactionDate: '2026-11-10', type: 'EXPENSE' }]
function mountView() { return mount(StatisticsView, { global: { stubs } }) }

enableAutoUnmount(afterEach)
beforeEach(() => {
  vi.clearAllMocks()
  api.summary.mockReset().mockResolvedValue(summary)
  api.byCategory.mockReset().mockResolvedValue(category)
  api.dailyTrend.mockReset().mockResolvedValue(trend)
  api.trend.mockReset().mockResolvedValue(trend)
  api.topExpenses.mockReset().mockResolvedValue(expenses)
})

describe('StatisticsView independent loading', () => {
  it('renders completed modules while another request is still pending, with a local retry on failure', async () => {
    const slow = deferred<typeof category>()
    api.byCategory.mockReturnValueOnce(slow.promise)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('1,000.00')
    expect(wrapper.text()).toContain('晚餐')
    expect(wrapper.findAll('canvas')).toHaveLength(1)
    slow.reject(new Error('unavailable'))
    await flushPromises()
    expect(wrapper.text()).toContain('分类统计加载失败')
    expect(wrapper.text()).toContain('晚餐')
    await wrapper.findAll('button').find(button => button.text() === '重试分类')!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('canvas')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('分类统计加载失败')
    expect(api.summary).toHaveBeenCalledTimes(1)
  })

  it('keeps charts mounted while refreshing and updates existing instances', async () => {
    const wrapper = mountView()
    await flushPromises()
    const canvas = wrapper.findAll('canvas').map(node => node.element)
    const nextSummary = deferred<typeof summary>()
    const nextCategories = deferred<typeof category>()
    const nextTrend = deferred<typeof trend>()
    api.summary.mockReturnValueOnce(nextSummary.promise)
    api.byCategory.mockReturnValueOnce(nextCategories.promise)
    api.dailyTrend.mockReturnValueOnce(nextTrend.promise)
    window.dispatchEvent(new Event('app:data-changed'))
    await flushPromises()
    expect(wrapper.findAll('canvas').map(node => node.element)).toEqual(canvas)
    expect(charts.destroyed).not.toHaveBeenCalled()
    nextSummary.resolve({ ...summary, totalExpense: 250 })
    nextCategories.resolve([{ ...category[0], amount: 250 }])
    nextTrend.resolve([{ ...trend[0], expense: 250 }])
    await flushPromises()
    expect(charts.created).toHaveBeenCalledTimes(2)
    expect(charts.updated).toHaveBeenCalledTimes(2)
    expect(charts.destroyed).not.toHaveBeenCalled()
  })

  it('ignores an older period response that completes after the latest period', async () => {
    const wrapper = mountView()
    await flushPromises()
    const older = deferred<typeof summary>()
    const newer = deferred<typeof summary>()
    api.summary.mockReturnValueOnce(older.promise).mockReturnValueOnce(newer.promise)
    await wrapper.find('#period-tab-last_3_months').trigger('click')
    await wrapper.find('#period-tab-last_6_months').trigger('click')
    newer.resolve({ ...summary, totalIncome: 6000 })
    await flushPromises()
    older.resolve({ ...summary, totalIncome: 3000 })
    await flushPromises()
    expect(wrapper.text()).toContain('6,000.00')
    expect(wrapper.text()).not.toContain('3,000.00')
    expect(wrapper.find('#period-tab-last_6_months').attributes('aria-selected')).toBe('true')
  })
})
