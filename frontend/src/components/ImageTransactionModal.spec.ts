import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImageTransactionModal from './ImageTransactionModal.vue'
import UiModal from './UiModal.vue'
import { ApiError } from '@/api/http'
import { useLedgerStore } from '@/stores/ledger'
import type { Account, ImageTransactionDraft, ImageTransactionParseResponse, Ledger, TransactionCapabilities } from '@/types'

const { parseImage, save, listCategories } = vi.hoisted(() => ({
  parseImage: vi.fn(), save: vi.fn(), listCategories: vi.fn()
}))

vi.mock('@/api', () => ({
  transactionsApi: { parseImage, save },
  categoriesApi: { list: listCategories }
}))

const capabilities: TransactionCapabilities = {
  imageParsingEnabled: true,
  maxImageSizeBytes: 5 * 1024 * 1024,
  supportedImageTypes: ['image/png', 'image/jpeg'],
  maxTransactionsPerImage: 20
}
const mounted: ReturnType<typeof mount>[] = []

beforeEach(() => {
  vi.clearAllMocks()
  parseImage.mockReset()
  save.mockReset()
  listCategories.mockResolvedValue([
    { id: 10, name: '餐饮', level: 1, type: 'EXPENSE' },
    { id: 20, name: '工资', level: 1, type: 'INCOME' }
  ])
  useLedgerStore().state.activeLedger = { id: 1, myRole: 'OWNER', name: '账本' } as Ledger
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL: vi.fn() }))
})

afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount())
  vi.unstubAllGlobals()
})

describe('截图识别与逐笔确认', () => {
  it('识别后允许修改，确认前不保存且不填入默认账户', async () => {
    parseImage.mockResolvedValue({ transactions: [draft()], warnings: ['请核对日期'] })
    save.mockResolvedValue({ id: 100 })
    const wrapper = await open()
    await upload(wrapper)
    await recognize(wrapper)

    expect(save).not.toHaveBeenCalled()
    expect(parseImage).toHaveBeenCalledWith(expect.any(File), 1, expect.any(AbortSignal))
    expect(wrapper.text()).toContain('请核对日期')
    expect((wrapper.get('#image-account').element as HTMLSelectElement).selectedIndex).toBe(0)
    await wrapper.get('#image-amount').setValue('36.5')
    await wrapper.get('#image-description').setValue('核对后的咖啡消费')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(save).toHaveBeenCalledWith(expect.objectContaining({ amount: 36.5, description: '核对后的咖啡消费', accountId: null }), 1)
    expect(wrapper.text()).toContain('已保存 1 笔')
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('逐笔保存，失败保留当前草稿且已保存行不能重复提交', async () => {
    parseImage.mockResolvedValue({ transactions: [draft(), draft({ amount: 2500, type: 'INCOME', categoryId: 20, description: '工资' })], warnings: [] })
    save.mockResolvedValueOnce({ id: 100 }).mockRejectedValueOnce(new ApiError('保存失败，请稍后重试', 400)).mockResolvedValueOnce({ id: 101 })
    const wrapper = await open()
    await upload(wrapper)
    await recognize(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect((wrapper.get('#image-description').element as HTMLInputElement).value).toBe('工资')
    expect(wrapper.findAll('.image-row-button')[0].attributes('disabled')).toBeDefined()

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('保存失败，请稍后重试')
    expect(wrapper.text()).toContain('待核对 1 笔 · 已保存 1 笔')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(save.mock.calls.map(call => call[0].description)).toEqual(['咖啡', '工资', '工资'])
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('已保存 2 笔')
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.text()).toContain('已保存 2 笔')
  })

  it('缺失日期、分类和修改收支类型后必须补齐再保存', async () => {
    parseImage.mockResolvedValue({ transactions: [draft({ transactionDate: null, categoryId: null })], warnings: [] })
    const wrapper = await open()
    await upload(wrapper)
    await recognize(wrapper)
    await wrapper.get('form').trigger('submit')
    expect(save).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请补全有效金额、日期和描述')
    await wrapper.get('#image-date').setValue('2026-11-10')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('请选择与收支类型一致的分类')
    await wrapper.get('#image-category').setValue('10')
    await wrapper.get('#image-type').setValue('INCOME')
    await wrapper.get('form').trigger('submit')
    expect(save).not.toHaveBeenCalled()
  })

  it('拒绝不支持、空白及超限图片，模型失败可重试', async () => {
    const wrapper = await open()
    await upload(wrapper, new File(['pdf'], 'bill.pdf', { type: 'application/pdf' }))
    expect(wrapper.text()).toContain('请选择 PNG 或 JPG')
    await upload(wrapper, new File([], 'empty.png', { type: 'image/png' }))
    expect(wrapper.text()).toContain('请选择非空且不超过 5 MB')
    const oversized = new File(['image'], 'large.png', { type: 'image/png' })
    Object.defineProperty(oversized, 'size', { value: capabilities.maxImageSizeBytes + 1 })
    await upload(wrapper, oversized)
    expect(parseImage).not.toHaveBeenCalled()
    expect(wrapper.find('img').exists()).toBe(false)

    parseImage.mockRejectedValueOnce(new ApiError('图片识别暂不可用', 400))
    await upload(wrapper)
    await recognize(wrapper)
    expect(wrapper.text()).toContain('图片识别暂不可用')
    expect(wrapper.find('img').exists()).toBe(true)
    expect(save).not.toHaveBeenCalled()
    parseImage.mockResolvedValue({ transactions: [draft()], warnings: [] })
    await recognize(wrapper)
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('切换账本时清除图片并忽略识别的迟到结果', async () => {
    let resolve!: (value: ImageTransactionParseResponse) => void
    parseImage.mockImplementationOnce(() => new Promise(result => { resolve = result }))
    const wrapper = await open()
    await upload(wrapper)
    await button(wrapper, '识别这张截图').trigger('click')
    const signal = parseImage.mock.calls[0][2] as AbortSignal
    useLedgerStore().state.activeLedger = { id: 2, myRole: 'OWNER', name: '另一个账本' } as Ledger
    await flushPromises()
    resolve({ transactions: [draft()], warnings: [] })
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
  })

  it('切换账本后的保存回调不能恢复旧交易或触发新账本已保存提示', async () => {
    let resolve!: (value: unknown) => void
    parseImage.mockResolvedValue({ transactions: [draft()], warnings: [] })
    save.mockImplementationOnce(() => new Promise(result => { resolve = result }))
    const wrapper = await open()
    await upload(wrapper)
    await recognize(wrapper)
    await wrapper.get('form').trigger('submit')
    useLedgerStore().state.activeLedger = { id: 2, myRole: 'OWNER', name: '另一个账本' } as Ledger
    await flushPromises()
    resolve({ id: 10 })
    await flushPromises()
    expect(save).toHaveBeenCalledWith(expect.any(Object), 1)
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('只读角色不能识别或保存已有草稿', async () => {
    parseImage.mockResolvedValue({ transactions: [draft()], warnings: [] })
    const wrapper = await open()
    await upload(wrapper)
    await recognize(wrapper)
    useLedgerStore().state.activeLedger!.myRole = 'VIEWER'
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(save).not.toHaveBeenCalled()
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
  })

  it('保存期间阻止关闭，权限变化后仍保留已提交成功的状态', async () => {
    let resolve!: (value: unknown) => void
    parseImage.mockResolvedValue({ transactions: [draft()], warnings: [] })
    save.mockImplementationOnce(() => new Promise(result => { resolve = result }))
    const wrapper = await open()
    await upload(wrapper)
    await recognize(wrapper)
    await wrapper.get('form').trigger('submit')
    wrapper.findComponent(UiModal).vm.$emit('update:modelValue', false)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    useLedgerStore().state.activeLedger!.myRole = 'VIEWER'
    await flushPromises()
    resolve({ id: 100 })
    await flushPromises()
    expect(wrapper.text()).toContain('已保存 1 笔')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })
})

async function open() {
  const wrapper = mount(ImageTransactionModal, {
    props: {
      modelValue: true,
      capabilities,
      accounts: [{ id: 1, active: true, default: true, name: '默认账户' } as Account]
    },
    global: {
      stubs: {
        UiModal: { template: '<div><slot /></div>' },
        CategorySelector: {
          props: ['modelValue', 'inputId', 'categories'],
          emits: ['update:modelValue'],
          template: '<select :id="inputId" :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><option value="">请选择</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select>'
        }
      }
    }
  })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

async function upload(wrapper: Awaited<ReturnType<typeof open>>, file = new File(['image'], 'bill.png', { type: 'image/png' })) {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

async function recognize(wrapper: Awaited<ReturnType<typeof open>>) {
  await button(wrapper, '识别这张截图').trigger('click')
  await flushPromises()
}

function button(wrapper: Awaited<ReturnType<typeof open>>, label: string) {
  const candidate = wrapper.findAll('button').find(item => item.text().includes(label))
  if (!candidate) throw new Error(`按钮未找到: ${label}`)
  return candidate
}

function draft(overrides: Partial<ImageTransactionDraft> = {}): ImageTransactionDraft {
  return {
    amount: 35, type: 'EXPENSE', categoryId: 10, description: '咖啡',
    originalText: '截图识别：星巴克咖啡35元', transactionDate: '2026-11-10',
    accountId: null, parsedMerchant: '星巴克', ...overrides
  }
}
