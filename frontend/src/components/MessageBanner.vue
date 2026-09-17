<template>
  <div
    v-if="visible"
    class="message t-panel-slide"
    :class="displayType"
    :data-open="String(open)"
    :role="displayType === 'error' ? 'alert' : 'status'"
    :aria-live="displayType === 'error' ? 'assertive' : 'polite'"
    aria-atomic="true"
  >
    <span class="message-text">{{ displayMessage }}</span>
    <slot name="actions" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { cssMs } from '@/utils/css'

const props = withDefaults(
  defineProps<{
    message?: string
    type?: 'success' | 'error' | 'info'
    /** success/info 展示 4 秒后自动淡出；error 始终驻留。设为 false 可让所有类型驻留。 */
    autoDismiss?: boolean
  }>(),
  {
    message: '',
    type: 'info',
    autoDismiss: true
  }
)

const emit = defineEmits<{
  /** 自动淡出完成时触发（父组件状态里可能仍留有旧文案，可借此清空）。 */
  dismissed: []
}>()

const AUTO_DISMISS_MS = 4000

const visible = ref(Boolean(props.message))
const open = ref(false)
const displayMessage = ref(props.message)
const displayType = ref(props.type)
let closeTimer: number | undefined
let openFrame: number | undefined
let dismissTimer: number | undefined

watch(
  () => [props.message, props.type] as const,
  ([message, type]) => {
    if (message) {
      clearTimers()
      displayMessage.value = message
      displayType.value = type
      visible.value = true
      open.value = false
      void nextTick(() => {
        openFrame = window.requestAnimationFrame(() => {
          open.value = true
        })
      })
      if (props.autoDismiss && type !== 'error') {
        dismissTimer = window.setTimeout(() => closeBanner(true), AUTO_DISMISS_MS)
      }
      return
    }

    closeBanner()
  },
  { immediate: true }
)

onBeforeUnmount(clearTimers)

function closeBanner(fromAutoDismiss = false): void {
  clearTimers()
  if (!visible.value) return

  open.value = false
  closeTimer = window.setTimeout(() => {
    visible.value = false
    displayMessage.value = ''
    if (fromAutoDismiss) {
      emit('dismissed')
    }
  }, cssMs('--panel-close-dur', 350))
}

function clearTimers(): void {
  if (openFrame !== undefined) {
    window.cancelAnimationFrame(openFrame)
    openFrame = undefined
  }
  if (closeTimer !== undefined) {
    window.clearTimeout(closeTimer)
    closeTimer = undefined
  }
  if (dismissTimer !== undefined) {
    window.clearTimeout(dismissTimer)
    dismissTimer = undefined
  }
}
</script>

<style scoped>
.message {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.message-text {
  flex: 1;
  min-width: 0;
}
</style>
