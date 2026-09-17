import { computed, reactive } from 'vue'
import { authApi } from '@/api'
import { ApiError, clearSession } from '@/api/http'

const state = reactive({
  initialized: false,
  authenticated: false,
  username: '',
})

export function useAuthStore() {
  const isAuthenticated = computed(() => state.authenticated)

  async function initialize(): Promise<void> {
    if (state.initialized) return

    try {
      const result = await authApi.me()
      if (result) {
        setAuthenticated(result.username)
      } else {
        // 服务器明确返回 401（getOptional 解包为 null）：会话确定失效。
        forceLogout()
      }
    } catch (error) {
      if (error instanceof ApiError) {
        // 收到服务器明确响应（非 401 的 4xx/5xx）：会话状态可判定，按失效处理。
        forceLogout()
      }
      // 纯网络错误（fetch reject）：会话状态未知，保留本地账本选择等状态，
      // 保持未认证让路由守卫引导到登录页；网络恢复后刷新即可凭 cookie 恢复会话。
    } finally {
      state.initialized = true
    }
  }

  async function login(username: string, password: string): Promise<void> {
    const result = await authApi.login(username, password)
    setAuthenticated(result.username)
  }

  async function register(username: string, password: string): Promise<void> {
    const result = await authApi.register(username, password)
    setAuthenticated(result.username)
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
    clearSession()
    state.authenticated = false
    state.username = ''
  }

  function setAuthenticated(username: string): void {
    state.authenticated = true
    state.username = username
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
