import { nextTick, reactive } from 'vue'
import { ApiError } from '@/api/http'

export type MessageType = 'success' | 'error' | 'info'

export interface MessageState {
  text: string
  type: MessageType
}

/**
 * 页面级消息横幅状态（配合 MessageBanner 使用，非全局单例——每个视图各持一份）。
 *
 * 模板接线：
 *   <MessageBanner :message="message.text" :type="message.type" @dismissed="clear" />
 *
 * showError 统一解包 ApiError（带服务端文案），其余错误落 fallback 文案。
 */
export function useMessage() {
  const message = reactive<MessageState>({ text: '', type: 'info' })

  function show(type: MessageType, text: string): void {
    if (message.text === text && message.type === type) {
      // 同文案重复触发（横幅可能已自动淡出）：先清空、下一 tick 写回，
      // 让 MessageBanner 的 props watcher 观察到变化从而重新展示。
      message.text = ''
      void nextTick(() => {
        message.type = type
        message.text = text
      })
      return
    }
    message.type = type
    message.text = text
  }

  function showSuccess(text: string): void {
    show('success', text)
  }

  function showInfo(text: string): void {
    show('info', text)
  }

  function showError(error: unknown, fallback = '操作失败，请稍后重试'): void {
    show('error', error instanceof ApiError ? error.message || fallback : fallback)
  }

  function clear(): void {
    message.text = ''
  }

  return { message, show, showSuccess, showInfo, showError, clear }
}
