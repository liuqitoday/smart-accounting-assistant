import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import ManualTransactionModal from './ManualTransactionModal.vue'
import type { Account } from '@/types'

vi.mock('@/api', () => ({
  categoriesApi: { list: vi.fn().mockResolvedValue([]) },
  transactionsApi: { save: vi.fn(), updateTags: vi.fn() },
  transfersApi: { create: vi.fn() }
}))

describe('ManualTransactionModal 默认账户', () => {
  it('打开时把账户预填为默认账户', async () => {
    const wrapper = mount(ManualTransactionModal, {
      props: { modelValue: false, accounts: [account(1), account(2, { default: true })], tags: [] },
      attachTo: document.body
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()

    expect(select('#manual-account').value).toBe('2')
    wrapper.unmount()
  })

  it('没有默认账户时保持「未指定账户」', async () => {
    const wrapper = mount(ManualTransactionModal, {
      props: { modelValue: false, accounts: [account(1), account(2)], tags: [] },
      attachTo: document.body
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()

    expect(select('#manual-account').value).toBe('')
    wrapper.unmount()
  })

  it('默认账户已停用时不预填', async () => {
    const wrapper = mount(ManualTransactionModal, {
      props: {
        modelValue: false,
        accounts: [account(1), account(2, { default: true, active: false })],
        tags: []
      },
      attachTo: document.body
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()

    expect(select('#manual-account').value).toBe('')
    wrapper.unmount()
  })

  it('转账的转出账户不预填', async () => {
    const wrapper = mount(ManualTransactionModal, {
      props: { modelValue: false, accounts: [account(1), account(2, { default: true })], tags: [] },
      attachTo: document.body
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()
    const typeSelect = select('#manual-type')
    typeSelect.value = 'TRANSFER'
    typeSelect.dispatchEvent(new Event('change'))
    await nextTick()
    await nextTick()

    expect(select('#manual-from-account').value).toBe('')
    expect(select('#manual-to-account').value).toBe('')
    wrapper.unmount()
  })
})

function select(id: string): HTMLSelectElement {
  const element = document.body.querySelector<HTMLSelectElement>(id)
  if (!element) throw new Error(`select ${id} not rendered`)
  return element
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
