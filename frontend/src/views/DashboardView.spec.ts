import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'
import { useLedgerStore } from '@/stores/ledger'
import { useRecording } from '@/stores/recording'
import type { Ledger } from '@/types'

const { summary, recent, capabilities } = vi.hoisted(() => ({ summary: vi.fn(), recent: vi.fn(), capabilities: vi.fn() }))
vi.mock('@/api', () => ({
  statisticsApi: { summary, recent }, transactionsApi: { capabilities }
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
const mounted: ReturnType<typeof mount>[] = []
const stubs = { PageHeader: true, RouterLink: { template: '<a><slot /></a>' } }
function render() { const wrapper = mount(DashboardView, { global: { stubs } }); mounted.push(wrapper); return wrapper }
beforeEach(() => {
  vi.clearAllMocks()
  summary.mockResolvedValue({ totalIncome: 0, totalExpense: 0, balance: 0, transactionCount: 0 })
  recent.mockResolvedValue([])
  capabilities.mockResolvedValue({ imageParsingEnabled: false })
  useLedgerStore().state.activeLedger = { id: 1, name: '账本', myRole: 'OWNER' } as Ledger
  useRecording().close()
})
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()))

describe('首页快速记账与真实加载状态', () => {
  it('点击入口立即打开对应记账方式', async () => {
    capabilities.mockResolvedValue({ imageParsingEnabled: true })
    const wrapper = render()
    await flushPromises()
    for (const [label, mode] of [['说一句', 'text'], ['手动记账', 'manual'], ['截图记账', 'image']]) {
      await wrapper.findAll('button').find(button => button.text().includes(label))!.trigger('click')
      expect(useRecording().state).toMatchObject({ open: true, mode })
    }
  })
  it('首次加载使用骨架，失败不显示零收支', async () => {
    let reject!: (error: Error) => void
    summary.mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
    const wrapper = render()
    expect(wrapper.find('[aria-label="正在加载本月汇总"]').exists()).toBe(true)
    expect(wrapper.find('.overview-amount').exists()).toBe(false)
    reject(new Error('offline'))
    await flushPromises()
    expect(wrapper.text()).toContain('本月汇总暂时无法加载')
    expect(wrapper.find('.overview-amount').exists()).toBe(false)
  })
  it('刷新失败保留已有汇总，服务端真实0正常显示', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.find('.overview-amount').text()).toContain('0.00')
    summary.mockRejectedValueOnce(new Error('offline'))
    window.dispatchEvent(new Event('app:data-changed'))
    await flushPromises()
    expect(wrapper.text()).toContain('正在显示上次的数据')
    expect(wrapper.find('.overview-amount').text()).toContain('0.00')
  })
  it('多模态能力关闭或加载失败时隐藏截图入口', async () => {
    capabilities.mockRejectedValueOnce(new Error('unavailable'))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).not.toContain('截图记账')
  })
  it('只读账本所有记账入口禁用', async () => {
    useLedgerStore().state.activeLedger!.myRole = 'VIEWER'
    capabilities.mockResolvedValue({ imageParsingEnabled: true })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.findAll('.quick-record button').every(button => button.attributes('disabled') !== undefined)).toBe(true)
  })
})
