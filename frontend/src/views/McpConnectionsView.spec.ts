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
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: vi.fn().mockResolvedValue(undefined) } })
  api.list.mockResolvedValue([connection]); api.create.mockResolvedValue(issued); api.rotate.mockResolvedValue(issued)
  api.revoke.mockResolvedValue(undefined); api.calls.mockResolvedValue([])
})
const button = (wrapper: ReturnType<typeof mount>, text: string) => wrapper.findAll('button').find(b => b.text() === text)!

describe('MCP connections', () => {
  it('keeps configuration available after closing credentials without recovering the secret', async () => {
    const { wrapper } = view(); await flushPromises()
    await button(wrapper, '更换令牌').trigger('click')
    await button(wrapper, '确认更换').trigger('click'); await flushPromises()
    await button(wrapper, '我已保存').trigger('click'); await flushPromises()
    await button(wrapper, '连接详情').trigger('click'); await flushPromises()
    const details = wrapper.find('.connection-details')
    expect(details.find('#mcp-token').exists()).toBe(false)
    expect(details.html()).not.toContain(issued.token)
    expect(details.text()).toContain('完整令牌无法再次查看')
    expect(details.text()).toContain('令牌前缀不能用于连接')
    expect(details.text()).toContain('记账与查询')
    expect(details.text()).toContain('个人')
    expect(details.text()).toContain('创建时间')
    await button(wrapper, '复制服务地址').trigger('click'); await flushPromises()
    expect(navigator.clipboard.writeText).toHaveBeenLastCalledWith(new URL('/mcp', location.origin).href)
    await button(wrapper, '复制 HTTP 配置示例').trigger('click'); await flushPromises()
    const http = JSON.parse(vi.mocked(navigator.clipboard.writeText).mock.lastCall![0])
    expect(Object.values(http.mcpServers)).toEqual([{ type: 'http', url: new URL('/mcp', location.origin).href, headers: { Authorization: 'Bearer <专用令牌>' } }])
    expect(details.text()).toContain('已复制')
    await button(wrapper, '复制 stdio 配置示例').trigger('click'); await flushPromises()
    const stdio = JSON.parse(vi.mocked(navigator.clipboard.writeText).mock.lastCall![0])
    expect(Object.values(stdio.mcpServers)).toEqual([expect.objectContaining({ env: { ACCOUNTING_MCP_URL: new URL('/mcp', location.origin).href, ACCOUNTING_MCP_TOKEN: '<专用令牌>' } })])
    await button(wrapper, '关闭详情').trigger('click'); await flushPromises()
    await button(wrapper, '连接详情').trigger('click'); await flushPromises()
    expect(wrapper.find('.connection-details').text()).not.toContain('已复制')
    expect(wrapper.find('#mcp-token').exists()).toBe(false)
  })

  it.each(['REVOKED', 'EXPIRED'])('retains details for a %s connection and explains that it must be replaced', async status => {
    api.list.mockResolvedValue([{ ...connection, status, writable: false, ledgerIds: [2, 99], defaultLedgerId: null }])
    const { wrapper } = view(); await flushPromises()
    await button(wrapper, '连接详情').trigger('click')
    const details = wrapper.find('.connection-details')
    expect(details.text()).toContain('只读查询')
    expect(details.text()).toContain('家庭、账本 #99（已不可访问）')
    expect(details.text()).toContain('每次由助手确认')
    expect(details.text()).toContain('原令牌无法使用')
    expect(details.text()).toContain('新建连接')
    expect(details.findAll('button').some(item => item.text() === '更换令牌')).toBe(false)
    expect(details.find('#mcp-url').exists()).toBe(true)
  })

  it('keeps the same real modal open from details through rotation and clears credentials on close', async () => {
    const Host = defineComponent({ setup: () => () => h(KeepAlive, null, { default: () => h(McpConnectionsView) }) })
    const wrapper = mount(Host, { attachTo: document.body, global: { stubs: { ...stubs, UiModal: false } } })
    await flushPromises()
    await button(wrapper, '连接详情').trigger('click'); await flushPromises()
    const historyId = window.history.state.appModal
    const modal = new DOMWrapper(document.querySelector('[role="dialog"]')!)
    await modal.findAll('button').find(item => item.text() === '更换令牌')!.trigger('click'); await flushPromises()
    expect(modal.text()).toContain('原令牌立即失效')
    await modal.findAll('button').find(item => item.text() === '确认更换')!.trigger('click'); await flushPromises()
    await new Promise(resolve => window.setTimeout(resolve, 40))
    expect(window.history.state.appModal).toBe(historyId)
    expect(document.querySelector<HTMLTextAreaElement>('#mcp-token')?.value).toBe(issued.token)
    await modal.find('[aria-label="关闭"]').trigger('click'); await flushPromises()
    expect(document.querySelector('#mcp-token')).toBeNull()
    expect(api.rotate).toHaveBeenCalledWith('one')
  })

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
