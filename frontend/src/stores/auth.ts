import { computed, reactive } from 'vue'
import { authApi } from '@/api'
import { ApiError, clearSession } from '@/api/http'
import { advanceSessionEpoch, rememberSession, storedSession } from '@/utils/localState'
import { markCachedData } from '@/composables/useConnectivity'
import { useLedgerStore } from '@/stores/ledger'

const state = reactive({
  initialized: false,
  authenticated: false,
  username: '',
  offlineSession: false,
})
let initialization: Promise<void> | null = null
let sessionGeneration = 0

export function useAuthStore() {
  const isAuthenticated = computed(() => state.authenticated)

  async function restore(): Promise<void> {
    const generation = sessionGeneration
    try {
      const result = await authApi.me()
      if (generation !== sessionGeneration) return
      if (result) {
        setAuthenticated(result.username)
      } else {
        // 服务器明确返回 401（getOptional 解包为 null）：会话确定失效。
        forceLogout()
      }
    } catch (error) {
      if (generation !== sessionGeneration) return
      if (error instanceof ApiError && error.status && error.status < 500) {
        forceLogout()
      } else {
        const saved = storedSession()
        if (saved) {
          state.authenticated = true
          state.username = saved.username
          state.offlineSession = true
          markCachedData()
        }
      }
    } finally {
      if (generation === sessionGeneration) state.initialized = true
    }
  }

  async function initialize(force = false): Promise<void> {
    if (initialization) return initialization
    if (state.initialized && !force) return
    const promise = restore().finally(() => { if (initialization === promise) initialization = null })
    initialization = promise
    return initialization
  }

  async function login(username: string, password: string): Promise<void> {
    advanceSessionEpoch()
    const generation = ++sessionGeneration
    initialization = null
    const result = await authApi.login(username, password)
    if (generation === sessionGeneration) { setAuthenticated(result.username); state.initialized = true }
  }

  async function register(username: string, password: string): Promise<void> {
    advanceSessionEpoch()
    const generation = ++sessionGeneration
    initialization = null
    const result = await authApi.register(username, password)
    if (generation === sessionGeneration) { setAuthenticated(result.username); state.initialized = true }
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 即使服务端调用失败，本地仍清理登录状态。
    }
    forceLogout()
  }

  /** 仅清理本地登录状态，不调服务端接口（用于 session 失效时强制登出）。 */
  function forceLogout(): void {
    sessionGeneration += 1
    initialization = null
    clearSession()
    useLedgerStore().reset()
    state.authenticated = false
    state.username = ''
    state.offlineSession = false
    state.initialized = true
  }

  function setAuthenticated(username: string): void {
    const previous = storedSession()?.username
    if (previous && previous !== username) {
      clearSession()
      useLedgerStore().reset()
    }
    state.authenticated = true
    state.username = username
    state.offlineSession = false
    rememberSession(username)
  }

  return {
    state,
    isAuthenticated,
    initialize,
    login,
    register,
    logout,
    forceLogout,
  }
}
