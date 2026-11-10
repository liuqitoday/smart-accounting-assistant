import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ManualTransactionModal from './ManualTransactionModal.vue'
import { useLedgerStore } from '@/stores/ledger'
import { setConnectionAvailable } from '@/composables/useConnectivity'
import type { AccountOption, Ledger } from '@/types'

const { submit, transfer, listCategories } = vi.hoisted(() => ({ submit: vi.fn(), transfer: vi.fn(), listCategories: vi.fn() }))
vi.mock('@/api', () => ({
  categoriesApi: { list: listCategories },
  transfersApi: { create: transfer }
}))
vi.mock('@/stores/outbox', () => ({ saveOrQueueTransaction: submit }))
const mounted: ReturnType<typeof mount>[] = []
beforeEach(() => {
  localStorage.clear()
  localStorage.setItem('activeLedgerId', '1')
  vi.clearAllMocks()
  submit.mockReset().mockResolvedValue({ queued: false, transaction: { id: 10 } })
  listCategories.mockReset().mockResolvedValue([{ id: 10, name: '餐饮', type: 'EXPENSE', level: 1 }])
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
  setConnectionAvailable(true)
  useLedgerStore().state.activeLedger = { id: 1, myRole: 'OWNER' } as Ledger
})
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()))
function render(accounts = [account(1), account(2, { default: true })], realCategorySelector = false) {
  const wrapper = mount(ManualTransactionModal, {
    props: { modelValue: true, embedded: true, accounts, tags: [{ id: 5, name: '旅行', color: '#000' }] },
    global: { stubs: {
      CategorySelector: realCategorySelector ? false : { props: ['modelValue', 'inputId'], emits: ['update:modelValue'], template: '<select :id="inputId" :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><option value="">请选择</option><option value="10">餐饮</option></select>' },
      TagSelector: { props: ['selectedIds'], emits: ['update:selectedIds'], template: '<button type="button" class="select-test-tag" @click="$emit(\'update:selectedIds\', [5])">旅行</button>' }
    } }
  })
  mounted.push(wrapper)
  return wrapper
}
async function fill(wrapper: ReturnType<typeof render>) {
  await flushPromises()
  await wrapper.get('#manual-amount').setValue('25')
  await wrapper.get('#manual-category').setValue('10')
}

describe('手动草稿和可靠保存', () => {
  it('预填启用的默认账户，并在没有或停用默认账户时保持未指定', async () => {
    for (const [accounts, expected] of [
      [[account(1), account(2, { default: true })], '2'],
      [[account(1), account(2)], ''],
      [[account(1), account(2, { default: true, active: false })], '']
    ] as [AccountOption[], string][]) {
      localStorage.clear()
      const wrapper = render(accounts)
      await flushPromises()
      expect((wrapper.get('#manual-account').element as HTMLSelectElement).value).toBe(expected)
      wrapper.unmount()
    }
  })
  it('关闭、重开和组件重建后保留金额及用户选择的账户', async () => {
    const wrapper = render()
    await fill(wrapper)
    await wrapper.get('#manual-account').setValue('1')
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    expect((wrapper.get('#manual-amount').element as HTMLInputElement).value).toBe('25')
    wrapper.unmount()
    const restored = render()
    await flushPromises()
    expect((restored.get('#manual-amount').element as HTMLInputElement).value).toBe('25')
    expect((restored.get('#manual-account').element as HTMLSelectElement).value).toBe('1')
  })
  it('标签随交易一次提交，失败重试复用key，修改草稿后使用新key', async () => {
    submit.mockRejectedValue(new Error('服务暂不可用'))
    const wrapper = render()
    await fill(wrapper)
    await wrapper.get('.select-test-tag').trigger('click')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    const first = submit.mock.calls[0][0]
    expect(first).toMatchObject({ tagIds: [5], description: '餐饮', amount: 25, clientRequestId: expect.any(String) })
    expect(submit.mock.calls[1][0].clientRequestId).toBe(first.clientRequestId)
    await wrapper.get('#manual-amount').setValue('26')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(submit.mock.calls[2][0].clientRequestId).not.toBe(first.clientRequestId)
  })
  it('离线保存排队后清空草稿并发出待同步状态', async () => {
    setConnectionAvailable(false)
    submit.mockResolvedValue({ queued: true })
    const wrapper = render()
    await fill(wrapper)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.emitted('saved')).toEqual([[{ queued: true }]])
    expect((wrapper.get('#manual-amount').element as HTMLInputElement).value).toBe('')
  })
  it('真实分类选择器点击子分类后更新显示、持久化并可离线提交', async () => {
    listCategories.mockResolvedValue([{ id: 10, name: '餐饮', type: 'EXPENSE', level: 1,
      children: [{ id: 554619516729, name: '中餐', parentName: '餐饮', parentId: 10, level: 2, type: 'EXPENSE' }] }])
    submit.mockResolvedValue({ queued: true })
    const wrapper = render([], true)
    await flushPromises()
    await wrapper.get('#manual-amount').setValue('28')
    await wrapper.get('#manual-description').setValue('离线同步验证午餐')
    wrapper.get('#manual-category').element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))
    await flushPromises()
    await new Promise(resolve => requestAnimationFrame(resolve))
    const choice = wrapper.findAll('[role="option"]').find(option => option.text().includes('中餐'))!
    await choice.trigger('click')
    expect(wrapper.get('#manual-category').text()).toContain('餐饮 / 中餐')
    const stored = JSON.parse(localStorage.getItem('accounting:v1:draft:anonymous:1:manual-transaction')!)
    expect(stored.value.form.categoryId).toBe(554619516729)
    setConnectionAvailable(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 554619516729, amount: 28, description: '离线同步验证午餐' }), 1)
    expect(wrapper.emitted('saved')).toEqual([[{ queued: true }]])
  })

  it('转账账户不默认选中，离线不提交转账', async () => {
    const wrapper = render()
    await wrapper.get('#manual-type').setValue('TRANSFER')
    expect((wrapper.get('#manual-from-account').element as HTMLSelectElement).value).toBe('')
    setConnectionAvailable(false)
    await wrapper.get('form').trigger('submit')
    expect(transfer).not.toHaveBeenCalled()
  })
})
function account(id: number, overrides: Partial<AccountOption> = {}): AccountOption {
  return { id, name: `账户${id}`, type: 'CASH', active: true, ...overrides }
}
