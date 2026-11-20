import { defineComponent, h, KeepAlive, ref } from 'vue'
import { DOMWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), rotate: vi.fn(), revoke: vi.fn(), calls: vi.fn() }))
vi.mock('@/api/mcp', () => ({ mcpApi: api }))
vi.mock('@/stores/ledger', () => ({ useLedgerStore: () => ({ state: { ledgers: [
  { id: 1, name: '个人', myRole: 'OWNER' }, { id: 2, name: '家庭', myRole: 'VIEWER' }
] } }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: vi.fn().mockResolvedValue(true) }) }))
import McpConnectionsView from './McpConnectionsView.vue'
import { closeModalsForNavigation } from '@/utils/modalHistory'

const connection = { id: 'one', name: '个人助手', tokenPrefix: 'aa_mcp_preview', scopes: [], ledgerIds: [1], defaultLedgerId: 1,
  writable: true, status: 'ACTIVE', createdAt: '2026-11-20T01:00:00Z', expiresAt: '2027-02-20T01:00:00Z', lastUsedAt: null }
const issued = { connection, token: '{{SECRET_0ztc2jag}}' }
const stubs = {
  PageHeader: { template: '<header><slot name="actions" /></header>' },
  UiModal: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /></section>' },
  MessageBanner: { props: ['message'], template: '<p>{{ message }}</p>' }
}
function view() {
  const visible = ref(true)
  const Host = defineComponent({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(McpConnectionsView) : h('div') }) })
  return { wrapper: mount(Host, { global: { stubs } }), visible }
}
enableAutoUnmount(afterEach)
afterEach(() => { closeModalsForNavigation() })
beforeEach(() => {
  vi.clearAllMocks()
  window.scrollTo = vi.fn()
  api.list.mockResolvedValue([connection]); api.create.mockResolvedValue(issued); api.rotate.mockResolvedValue(issued)
  api.revoke.mockResolvedValue(undefined); api.calls.mockResolvedValue([])
})
const button = (wrapper: ReturnType<typeof mount>, text: string) => wrapper.findAll('button').find(b => b.text() === text)!

describe('MCP connections', () => {
  it('keeps the real modal history entry open when advancing to the one-time token', async () => {
    const Host = defineComponent({ setup: () => () => h(KeepAlive, null, { default: () => h(McpConnectionsView) }) })
    const wrapper = mount(Host, { attachTo: document.body, global: { stubs: { ...stubs, UiModal: false } } })
    await flushPromises()
    await button(wrapper, '新建连接').trigger('click'); await flushPromises()
    const historyId = window.history.state.appModal
    const modal = new DOMWrapper(document.querySelector('[role="dialog"]')!)
    await modal.find('#mcp-name').setValue('真实弹窗')
    await modal.find('input[type=checkbox]').setValue(true)
    await modal.find('form').trigger('submit'); await flushPromises()
    await new Promise(resolve => window.setTimeout(resolve, 40))
    expect(window.history.state.appModal).toBe(historyId)
    expect(document.querySelector<HTMLTextAreaElement>('#mcp-token')?.value).toBe(issued.token)
    expect(document.body.textContent).toContain('保存连接信息')
  })

  it('requires selected ledgers and clears a default when its grant is removed', async () => {
    const { wrapper } = view(); await flushPromises()
    await button(wrapper, '新建连接').trigger('click')
    expect(button(wrapper, '创建连接').attributes('disabled')).toBeDefined()
    await wrapper.find('#mcp-name').setValue('新助手')
    await wrapper.findAll('input[type=checkbox]')[0].setValue(true)
    await wrapper.find('#mcp-default').setValue('1')
    await wrapper.findAll('input[type=checkbox]')[0].setValue(false)
    await wrapper.findAll('input[type=checkbox]')[1].setValue(true)
    await wrapper.find('form').trigger('submit'); await flushPromises()
    expect(api.create).toHaveBeenCalledWith({ name: '新助手', ledgerIds: [2], defaultLedgerId: null, writable: false, expiresInDays: 90 })
    expect(wrapper.find<HTMLTextAreaElement>('#mcp-token').element.value).toBe(issued.token)
    await button(wrapper, '我已保存').trigger('click'); await flushPromises()
    expect(wrapper.find('#mcp-token').exists()).toBe(false)
    expect(localStorage.length).toBe(0)
  })

  it('does not restore a newly issued secret after navigating away during creation', async () => {
    let resolve!: (value: typeof issued) => void
    api.create.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const { wrapper, visible } = view(); await flushPromises()
    await button(wrapper, '新建连接').trigger('click')
    await wrapper.find('#mcp-name').setValue('助手')
    await wrapper.findAll('input[type=checkbox]')[0].setValue(true)
    await wrapper.find('form').trigger('submit')
    visible.value = false; await flushPromises()
    resolve(issued); await flushPromises()
    visible.value = true; await flushPromises()
    expect(wrapper.find('#mcp-token').exists()).toBe(false)
  })

  it('shows list and call failures with retry instead of an empty state', async () => {
    api.list.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = view(); await flushPromises()
    expect(wrapper.text()).toContain('连接列表加载失败')
    await button(wrapper, '重试').trigger('click'); await flushPromises()
    api.calls.mockRejectedValueOnce(new Error('offline'))
    await button(wrapper, '调用记录').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('调用记录加载失败')
    await button(wrapper, '重试').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('还没有调用记录')
  })

  it('rotates a connection and clears the secret when the view is deactivated', async () => {
    const { wrapper, visible } = view(); await flushPromises()
    await button(wrapper, '更换令牌').trigger('click'); await flushPromises()
    await button(wrapper, '确认更换').trigger('click'); await flushPromises()
    expect(api.rotate).toHaveBeenCalledWith('one')
    expect(wrapper.find('#mcp-token').exists()).toBe(true)
    visible.value = false; await flushPromises()
    visible.value = true; await flushPromises()
    expect(wrapper.find('#mcp-token').exists()).toBe(false)
    await button(wrapper, '撤销连接').trigger('click'); await flushPromises()
    expect(api.revoke).toHaveBeenCalledWith('one')
  })
})
