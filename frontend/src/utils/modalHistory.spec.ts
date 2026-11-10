import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import { closeModalsForNavigation, registerModal, unregisterModal } from './modalHistory'

let router: Router
const routeChanged = vi.fn()

beforeEach(async () => {
  closeModalsForNavigation()
  window.history.replaceState({ position: 0 }, '', '/transactions')
  router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/transactions', component: { template: '<div>账单</div>' } },
      { path: '/statistics', component: { template: '<div>统计</div>' } }
    ]
  })
  router.beforeEach((to, from) => {
    if (to.fullPath !== from.fullPath) closeModalsForNavigation()
  })
  router.afterEach(routeChanged)
  await router.push('/transactions')
  await router.isReady()
  routeChanged.mockClear()
})

afterEach(() => {
  closeModalsForNavigation()
  router.options.history.destroy()
})

describe('弹窗与浏览器真实返回栈', () => {
  it('浏览器返回先关闭当前弹窗，并保持当前路由', async () => {
    const close = vi.fn()
    registerModal('edit', close)
    expect(window.history.state.appModal).toBe('edit')
    window.history.back()
    await vi.waitFor(() => expect(close).toHaveBeenCalledTimes(1))
    expect(window.location.pathname).toBe('/transactions')
    expect(router.currentRoute.value.path).toBe('/transactions')
    expect(window.history.state.appModal).toBeUndefined()
    expect(routeChanged).not.toHaveBeenCalled()
  })

  it('程序关闭后立即导航，不会吞掉之后的真正页面返回', async () => {
    registerModal('edit', vi.fn())
    unregisterModal('edit')
    await router.push('/statistics')
    // The queued modal traversal is cancelled by the navigation pushState.
    await new Promise(resolve => window.setTimeout(resolve, 20))
    expect(window.location.pathname).toBe('/statistics')
    expect(router.currentRoute.value.path).toBe('/statistics')
    routeChanged.mockClear()
    window.history.back()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/transactions'))
    expect(window.location.pathname).toBe('/transactions')
    expect(routeChanged).toHaveBeenCalledTimes(1)
  })

  it('程序关闭嵌套弹窗只退到父弹窗，不会误关父弹窗或触发路由', async () => {
    const closeParent = vi.fn()
    const closeChild = vi.fn()
    registerModal('parent', closeParent)
    registerModal('child', closeChild)
    unregisterModal('child')
    await vi.waitFor(() => expect(window.history.state.appModal).toBe('parent'))
    expect(closeParent).not.toHaveBeenCalled()
    expect(closeChild).not.toHaveBeenCalled()
    expect(routeChanged).not.toHaveBeenCalled()
    window.history.back()
    await vi.waitFor(() => expect(closeParent).toHaveBeenCalledTimes(1))
    expect(closeChild).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/transactions')
    expect(routeChanged).not.toHaveBeenCalled()
  })

  it('逐次浏览器返回只关闭最上层弹窗', async () => {
    const closeParent = vi.fn()
    const closeChild = vi.fn(() => unregisterModal('child'))
    registerModal('parent', closeParent)
    registerModal('child', closeChild)
    window.history.back()
    await vi.waitFor(() => expect(closeChild).toHaveBeenCalledTimes(1))
    expect(closeParent).not.toHaveBeenCalled()
    expect(window.history.state.appModal).toBe('parent')
    window.history.back()
    await vi.waitFor(() => expect(closeParent).toHaveBeenCalledTimes(1))
    expect(routeChanged).not.toHaveBeenCalled()
  })
})
