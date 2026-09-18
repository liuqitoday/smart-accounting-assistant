import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'
import { useLedgerStore } from '@/stores/ledger'
import type { Account, Ledger, TransactionParseResponse } from '@/types'

const parseOnly = vi.fn()
const listAccounts = vi.fn()

vi.mock('@/api', () => ({
  accountsApi: { list: (...args: unknown[]) => listAccounts(...args) },
  categoriesApi: { list: vi.fn().mockResolvedValue([]) },
  tagsApi: { list: vi.fn().mockResolvedValue([]) },
  statisticsApi: { summary: vi.fn().mockResolvedValue({ totalIncome: 0, totalExpense: 0, balance: 0, transactionCount: 0 }) },
  transactionsApi: { parseOnly: (...args: unknown[]) => parseOnly(...args), save: vi.fn() }
}))

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))

if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    media: '',
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
    onchange: null
  }) as unknown as typeof window.matchMedia
}

const stubs = {
  AppShell: { template: '<div><slot /></div>' },
  PageHeader: { template: '<div />' },
  MessageBanner: { template: '<div />' },
  ManualTransactionModal: { template: '<div />' },
  CategorySelector: { template: '<div />' },
  TagSelector: { template: '<div />' }
}

describe('DashboardView AI 解析后的默认账户', () => {
  beforeEach(() => {
    parseOnly.mockReset()
    listAccounts.mockReset()
    useLedgerStore().state.activeLedger = { id: 1, name: '账本', myRole: 'OWNER' } as Ledger
  })

  it('解析结果未带账户时自动选中默认账户', async () => {
    listAccounts.mockResolvedValue([account(1), account(2, { default: true })])
    parseOnly.mockResolvedValue(parsed(null))

    const wrapper = await parse()
    expect(accountSelect(wrapper).value).toBe('2')
  })

  it('没有默认账户时保持「未指定账户」', async () => {
    listAccounts.mockResolvedValue([account(1), account(2)])
    parseOnly.mockResolvedValue(parsed(null))

    const wrapper = await parse()
    expect(accountSelect(wrapper).value).toBe('')
  })

  it('默认账户已停用时不自动选中', async () => {
    listAccounts.mockResolvedValue([account(1), account(2, { default: true, active: false })])
    parseOnly.mockResolvedValue(parsed(null))

    const wrapper = await parse()
    expect(accountSelect(wrapper).value).toBe('')
  })

  it('AI 已经给出账户时不覆盖它', async () => {
    listAccounts.mockResolvedValue([account(1), account(2, { default: true })])
    parseOnly.mockResolvedValue(parsed(1))

    const wrapper = await parse()
    expect(accountSelect(wrapper).value).toBe('1')
  })
})

async function parse() {
  const wrapper = mount(DashboardView, { global: { stubs } })
  await flushPromises()
  await wrapper.find('#transactionText').setValue('星巴克咖啡 35 元')
  const button = wrapper.findAll('button').find(candidate => candidate.text().includes('解析这笔账'))
  if (!button) throw new Error('解析按钮未渲染')
  await button.trigger('click')
  await flushPromises()
  return wrapper
}

function accountSelect(wrapper: Awaited<ReturnType<typeof parse>>): HTMLSelectElement {
  const element = wrapper.find('#parsed-account').element
  return element as HTMLSelectElement
}

function parsed(accountId: number | null): TransactionParseResponse {
  return {
    amount: 35,
    type: 'EXPENSE',
    description: '星巴克咖啡',
    originalText: '星巴克咖啡 35 元',
    categoryId: 10,
    transactionDate: '2026-09-18',
    accountId
  }
}

function account(id: number, overrides: Partial<Account> = {}): Account {
  return {
    id,
    name: `账户${id}`,
    type: 'CASH',
    initialBalance: 0,
    currentBalance: 0,
    active: true,
    transactionCount: 0,
    ...overrides
  }
}
