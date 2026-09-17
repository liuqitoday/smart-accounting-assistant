import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ResultCard from './ResultCard.vue'
import type { AnalysisAggregateResult, AnalysisBreakdownResult, AnalysisUnknownResult } from '@/types'

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

const period = { current: { start: '2026-08-01', end: '2026-08-25' } }

describe('ResultCard', () => {
  it('shows 标签关联占比 for V2 breakdown association share', () => {
    const card: AnalysisBreakdownResult = {
      kind: 'BREAKDOWN',
      title: '分类构成',
      metric: 'SUM',
      unit: 'CNY',
      transactionType: 'EXPENSE',
      period,
      filters: {},
      warnings: [{ code: 'ASSOCIATION_SHARE', message: '标签关联占比' }],
      associationShare: true,
      rows: [
        {
          id: 1,
          label: '餐饮',
          parentLabel: '生活',
          value: '120.00',
          count: 2,
          percentage: '60.00',
        },
      ],
    }
    const wrapper = mount(ResultCard, { props: { card } })
    expect(wrapper.text()).toContain('标签关联占比')
    expect(wrapper.text()).toContain('餐饮')
  })

  it('renders V2 aggregate with named values', () => {
    const card: AnalysisAggregateResult = {
      kind: 'AGGREGATE',
      title: '本月支出',
      metric: 'SUM',
      unit: 'CNY',
      transactionType: 'EXPENSE',
      period,
      filters: {},
      warnings: [],
      values: { value: '3280.5', count: 12 },
    }
    const wrapper = mount(ResultCard, { props: { card } })
    expect(wrapper.text()).toContain('本月支出')
    expect(wrapper.text()).toContain('¥3,280.50')
    expect(wrapper.text()).toContain('2026-08-01 ~ 2026-08-25')
    expect(wrapper.text()).toContain('支出')
    expect(wrapper.text()).toContain('查看明细')
  })

  it('shows a visible warning for UNKNOWN kind instead of a blank card', () => {
    const card: AnalysisUnknownResult = {
      kind: 'UNKNOWN',
      title: '',
      metric: 'SUM',
      unit: 'CNY',
      transactionType: null,
      period,
      filters: {},
      warnings: [{ code: 'UNKNOWN_KIND', message: '无法展示该结果类型' }],
      originalKind: 'UNKNOWN_KIND',
      raw: { kind: 'UNKNOWN_KIND' },
    }
    const wrapper = mount(ResultCard, { props: { card } })
    expect(wrapper.text().trim().length).toBeGreaterThan(0)
    expect(wrapper.text()).toMatch(/无法展示|不支持|未知/)
    expect(wrapper.text()).not.toContain('查看明细')
  })
})
