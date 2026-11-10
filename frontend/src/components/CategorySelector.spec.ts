import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CategorySelector from './CategorySelector.vue'

class TestViewport extends EventTarget {
  offsetTop = 0
  offsetLeft = 0
  width = 390
  height = 844
}

let viewport: TestViewport
const mounted: ReturnType<typeof mount>[] = []
const hosts: HTMLElement[] = []

beforeEach(() => {
  vi.useFakeTimers()
  viewport = new TestViewport()
  vi.stubGlobal('visualViewport', viewport)
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => window.setTimeout(() => callback(0), 16))
  vi.stubGlobal('cancelAnimationFrame', (id: number) => window.clearTimeout(id))
})

afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount())
  hosts.splice(0).forEach(host => host.remove())
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function rect(top: number, left = 20, width = 350, height = 42): DOMRect {
  return { top, left, width, height, right: left + width, bottom: top + height, x: left, y: top, toJSON() {} }
}

function render(position: DOMRect, clip?: DOMRect) {
  const host = document.createElement('div')
  document.body.append(host)
  hosts.push(host)
  if (clip) {
    host.style.overflow = 'auto'
    vi.spyOn(host, 'getBoundingClientRect').mockReturnValue(clip)
  }
  const wrapper = mount(CategorySelector, {
    attachTo: host,
    props: {
      modelValue: null,
      allowEmpty: false,
      categories: [{ id: 1, name: '餐饮' }, { id: 554619516729, name: '中餐', parentName: '餐饮' }]
    }
  })
  mounted.push(wrapper)
  vi.spyOn(wrapper.element, 'getBoundingClientRect').mockReturnValue(position)
  return wrapper
}

async function frame(): Promise<void> {
  await nextTick()
  await vi.advanceTimersByTimeAsync(16)
  await nextTick()
}

async function open(wrapper: ReturnType<typeof render>, detail = 1): Promise<void> {
  wrapper.get('.cs-trigger').element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail }))
  await frame()
}

describe('分类列表在手机可见区域内定位', () => {
  it('下方充足时向下展开，触摸打开不自动弹出搜索键盘，选中后保留数字ID', async () => {
    const wrapper = render(rect(100))
    await open(wrapper)
    const list = wrapper.get<HTMLElement>('.cs-dropdown')
    expect(list.attributes('data-placement')).toBe('below')
    expect(list.attributes('data-origin')).toBe('top-left')
    expect(list.element.style.maxHeight).toBe('384px')
    expect(list.element.style.top).toBe('48px')
    expect(document.activeElement).not.toBe(wrapper.get('.cs-search-input').element)

    await wrapper.findAll('[role="option"]')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[554619516729]])
    expect(document.activeElement).toBe(wrapper.get('.cs-trigger').element)
  })

  it('靠近屏幕底部时向上展开并反转动画原点', async () => {
    const wrapper = render(rect(590))
    await open(wrapper)
    const list = wrapper.get<HTMLElement>('.cs-dropdown')
    expect(list.attributes('data-placement')).toBe('above')
    expect(list.attributes('data-origin')).toBe('bottom-left')
    expect(list.element.style.bottom).toBe('48px')
    expect(list.element.style.maxHeight).toBe('384px')
  })

  it('同时受滚动弹窗裁剪边界和左右屏幕边界约束', async () => {
    const wrapper = render(rect(600, 230, 120), rect(300, 20, 350, 440))
    await open(wrapper)
    const list = wrapper.get<HTMLElement>('.cs-dropdown')
    expect(list.attributes('data-placement')).toBe('above')
    expect(list.element.style.maxHeight).toBe('286px')
    expect(list.element.style.width).toBe('320px')
    expect(list.element.style.left).toBe('-188px')
  })

  it('键盘缩小或平移visualViewport后调整方向、高度，触发框暂时被键盘覆盖时列表仍留在可见区', async () => {
    const wrapper = render(rect(300))
    await open(wrapper)
    expect(wrapper.get('.cs-dropdown').attributes('data-placement')).toBe('below')

    viewport.height = 480
    viewport.dispatchEvent(new Event('resize'))
    await frame()
    const list = wrapper.get<HTMLElement>('.cs-dropdown')
    expect(list.attributes('data-placement')).toBe('above')
    expect(list.element.style.maxHeight).toBe('286px')

    viewport.offsetTop = 120
    viewport.height = 320
    viewport.dispatchEvent(new Event('scroll'))
    await frame()
    expect(list.element.style.maxHeight).toBe('166px')

    viewport.offsetTop = 0
    viewport.height = 250
    viewport.dispatchEvent(new Event('resize'))
    await frame()
    expect(list.element.style.maxHeight).toBe('234px')
    expect(list.element.style.bottom).toBe('100px')
  })

  it('键盘操作仍可聚焦搜索，关闭后停止监听视口和滚动', async () => {
    const viewportRemove = vi.spyOn(viewport, 'removeEventListener')
    const windowRemove = vi.spyOn(window, 'removeEventListener')
    const wrapper = render(rect(590))
    await open(wrapper, 0)
    expect(document.activeElement).toBe(wrapper.get('.cs-search-input').element)

    viewportRemove.mockClear()
    windowRemove.mockClear()
    await wrapper.get('.cs-search-input').trigger('keydown', { key: 'Escape' })
    expect(viewportRemove).toHaveBeenCalledWith('resize', expect.any(Function))
    expect(viewportRemove).toHaveBeenCalledWith('scroll', expect.any(Function))
    expect(windowRemove).toHaveBeenCalledWith('resize', expect.any(Function))
    expect(windowRemove).toHaveBeenCalledWith('scroll', expect.any(Function), true)
    await vi.advanceTimersByTimeAsync(150)
    expect(wrapper.find('.cs-dropdown').exists()).toBe(false)
  })
})
