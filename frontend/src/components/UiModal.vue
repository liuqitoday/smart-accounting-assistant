<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-backdrop" @click.self="requestClose">
      <div
        ref="panelRef"
        class="modal-panel t-modal"
        :class="[size, { 'is-open': isOpen, 'is-closing': isClosing }]"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="subtitle ? subtitleId : undefined"
        tabindex="-1"
      >
        <div class="modal-header">
          <div>
            <h2 :id="titleId">{{ title }}</h2>
            <p v-if="subtitle" :id="subtitleId">{{ subtitle }}</p>
          </div>
          <button class="icon-button" type="button" aria-label="关闭" @click="requestClose">
            <X />
          </button>
        </div>
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { cssMs } from '@/utils/css'
import { lockBodyScroll, unlockBodyScroll } from '@/utils/modalScrollLock'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    subtitle?: string
    size?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  {
    subtitle: '',
    size: 'md'
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = ref(props.modelValue)
const isOpen = ref(false)
const isClosing = ref(false)
const panelRef = ref<HTMLElement | null>(null)
const instanceId = useId().replace(/:/g, '')
const titleId = `modal-title-${instanceId}`
const subtitleId = `modal-subtitle-${instanceId}`
let closeTimer: number | undefined
let openFrame: number | undefined
let previousActiveElement: HTMLElement | null = null
let scrollLocked = false
let keydownListening = false

watch(
  () => props.modelValue,
  value => {
    if (value) {
      openModal()
    } else {
      closeModal()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  const shouldRestoreFocus = isTopmostModal()
  clearMotionTimers()
  stopKeydownListener()
  releaseScrollLock()
  if (shouldRestoreFocus) {
    restorePreviousFocus()
  } else {
    previousActiveElement = null
  }
})

function requestClose(): void {
  emit('update:modelValue', false)
}

function openModal(): void {
  clearMotionTimers()
  if (!visible.value || (!isOpen.value && !isClosing.value)) {
    previousActiveElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
  }
  visible.value = true
  isClosing.value = false
  isOpen.value = false
  acquireScrollLock()
  startKeydownListener()

  void nextTick(() => {
    openFrame = window.requestAnimationFrame(() => {
      isOpen.value = true
      const panel = panelRef.value
      if (panel && !panel.contains(document.activeElement)) {
        panel.focus({ preventScroll: true })
      }
    })
  })
}

function closeModal(): void {
  clearMotionTimers()
  if (!visible.value) return

  isOpen.value = false
  isClosing.value = true
  closeTimer = window.setTimeout(() => {
    visible.value = false
    isClosing.value = false
    stopKeydownListener()
    releaseScrollLock()
    restorePreviousFocus()
  }, cssMs('--modal-close-dur', 150))
}

function handleKeydown(event: KeyboardEvent): void {
  if (!isTopmostModal()) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    requestClose()
    return
  }
  if (event.key !== 'Tab') return

  const panel = panelRef.value
  if (!panel) return
  const focusable = Array.from(
    panel.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
  ).filter(element => element.getAttribute('aria-hidden') !== 'true' && element.offsetParent !== null)

  if (!focusable.length) {
    event.preventDefault()
    panel.focus({ preventScroll: true })
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || active === panel || !panel.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (active === last || active === panel || !panel.contains(active))) {
    event.preventDefault()
    first.focus()
  }
}

function isTopmostModal(): boolean {
  const panels = Array.from(document.querySelectorAll<HTMLElement>('.modal-panel'))
  let topmost: HTMLElement | undefined
  let topmostZIndex = Number.NEGATIVE_INFINITY
  for (const panel of panels) {
    const backdrop = panel.closest<HTMLElement>('.modal-backdrop')
    const zIndex = Number.parseFloat(backdrop ? window.getComputedStyle(backdrop).zIndex : '0') || 0
    if (zIndex >= topmostZIndex) {
      topmost = panel
      topmostZIndex = zIndex
    }
  }
  return topmost === panelRef.value
}

function startKeydownListener(): void {
  if (keydownListening) return
  document.addEventListener('keydown', handleKeydown, true)
  keydownListening = true
}

function stopKeydownListener(): void {
  if (!keydownListening) return
  document.removeEventListener('keydown', handleKeydown, true)
  keydownListening = false
}

function acquireScrollLock(): void {
  if (scrollLocked) return
  lockBodyScroll()
  scrollLocked = true
}

function releaseScrollLock(): void {
  if (!scrollLocked) return
  unlockBodyScroll()
  scrollLocked = false
}

function restorePreviousFocus(): void {
  const target = previousActiveElement
  previousActiveElement = null
  if (target?.isConnected) {
    target.focus({ preventScroll: true })
  }
}

function clearMotionTimers(): void {
  if (openFrame !== undefined) {
    window.cancelAnimationFrame(openFrame)
    openFrame = undefined
  }
  if (closeTimer !== undefined) {
    window.clearTimeout(closeTimer)
    closeTimer = undefined
  }
}
</script>
