import { reactive } from 'vue'

export interface ConfirmOptions {
  title: string
  message: string
  /** 确认按钮文案，默认「确认」 */
  confirmText?: string
  /** 取消按钮文案，默认「取消」 */
  cancelText?: string
  /** 危险操作：确认按钮用 .button.danger 样式，且默认聚焦「取消」 */
  danger?: boolean
}

interface ConfirmState {
  open: boolean
  title: string
  message: string
  confirmText: string
  cancelText: string
  danger: boolean
}

// 模块级单例：全局只有一个确认框宿主（App.vue 挂载 ConfirmModal）
const state = reactive<ConfirmState>({
  open: false,
  title: '',
  message: '',
  confirmText: '确认',
  cancelText: '取消',
  danger: false
})

let resolver: ((confirmed: boolean) => void) | null = null

/**
 * 替代 window.confirm 的确认框。
 *
 *   const { confirm } = useConfirm()
 *   if (!(await confirm({ title: '删除标签', message: '确认删除「餐饮」？', danger: true }))) return
 */
export function useConfirm() {
  return { confirm }
}

function confirm(options: ConfirmOptions): Promise<boolean> {
  // 上一个确认框尚未关闭就被新请求顶替：旧请求按「取消」结算
  resolver?.(false)
  state.title = options.title
  state.message = options.message
  state.confirmText = options.confirmText ?? '确认'
  state.cancelText = options.cancelText ?? '取消'
  state.danger = options.danger ?? false
  state.open = true
  return new Promise<boolean>(resolve => {
    resolver = resolve
  })
}

/** 仅供全局宿主 ConfirmModal.vue 使用，业务代码请走 useConfirm()。 */
export function useConfirmHost() {
  function settle(confirmed: boolean): void {
    state.open = false
    const resolve = resolver
    resolver = null
    resolve?.(confirmed)
  }

  return { state, settle }
}
