import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { isModalScrollLocked, lockBodyScroll, unlockBodyScroll } from '@/utils/modalScrollLock'

const mocks = vi.hoisted(() => ({ update: vi.fn() }))
vi.mock('virtual:pwa-register/vue', async () => {
  const { ref } = await import('vue')
  return { useRegisterSW: () => ({ needRefresh: ref(true), updateServiceWorker: mocks.update }) }
})
import PwaStatus from './PwaStatus.vue'

enableAutoUnmount(afterEach)
beforeEach(() => {
  mocks.update.mockReset().mockResolvedValue(undefined)
  window.scrollTo = vi.fn()
})
afterEach(() => {
  while (isModalScrollLocked.value) unlockBodyScroll()
})

describe('PWA更新提示与弹窗', () => {
  it('打开弹窗时暂缓提示，所有嵌套弹窗关闭后保留并重新展示更新', async () => {
    const wrapper = mount(PwaStatus)
    expect(wrapper.find('.pwa-update').exists()).toBe(true)
    lockBodyScroll()
    await nextTick()
    expect(wrapper.find('.pwa-update').exists()).toBe(false)
    lockBodyScroll()
    unlockBodyScroll()
    await nextTick()
    expect(wrapper.find('.pwa-update').exists()).toBe(false)
    unlockBodyScroll()
    await nextTick()
    expect(wrapper.find('.pwa-update').exists()).toBe(true)
    expect(mocks.update).not.toHaveBeenCalled()
    await wrapper.findAll('button').find(button => button.text() === '更新')!.trigger('click')
    expect(mocks.update).toHaveBeenCalledWith(true)
  })

  it('用户选择稍后后，关闭弹窗不会重新强制展示提示', async () => {
    const wrapper = mount(PwaStatus)
    await wrapper.findAll('button').find(button => button.text() === '稍后')!.trigger('click')
    expect(wrapper.find('.pwa-update').exists()).toBe(false)
    lockBodyScroll()
    await nextTick()
    unlockBodyScroll()
    await nextTick()
    expect(wrapper.find('.pwa-update').exists()).toBe(false)
  })
})
