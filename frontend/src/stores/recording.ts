import { reactive } from 'vue'

export type RecordingMode = 'text' | 'manual' | 'image'
const state = reactive({ open: false, mode: 'text' as RecordingMode, notice: '' })
let noticeTimer: ReturnType<typeof setTimeout> | undefined

export function openRecording(mode: RecordingMode = 'text'): void {
  state.mode = mode
  state.open = true
}

export function useRecording() {
  return {
    state,
    open: openRecording,
    close() { state.open = false },
    notifySaved(queued = false) {
      state.notice = queued ? '已保存到本机，联网后自动同步' : '已记下这一笔'
      clearTimeout(noticeTimer)
      noticeTimer = setTimeout(() => { state.notice = '' }, 3500)
    }
  }
}
