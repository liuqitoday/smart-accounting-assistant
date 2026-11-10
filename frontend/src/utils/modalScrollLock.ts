import { computed, ref } from 'vue'

const lockCount = ref(0)

/** Read-only overlay state shared by dialogs and global notices. */
export const isModalScrollLocked = computed(() => lockCount.value > 0)
let previousOverflow = ''
let previousPaddingRight = ''
let previousPosition = ''
let previousTop = ''
let previousWidth = ''
let lockedScroll = { left: 0, top: 0 }

export function pageScrollPosition(): { left: number; top: number } {
  return lockCount.value > 0 ? lockedScroll : { left: window.scrollX, top: window.scrollY }
}

export function lockBodyScroll(): void {
  if (lockCount.value === 0) {
    previousOverflow = document.body.style.overflow
    previousPaddingRight = document.body.style.paddingRight
    previousPosition = document.body.style.position
    previousTop = document.body.style.top
    previousWidth = document.body.style.width
    lockedScroll = { left: window.scrollX, top: window.scrollY }
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
    document.body.style.overflow = 'hidden'
    document.body.style.position = 'fixed'
    document.body.style.top = `-${lockedScroll.top}px`
    document.body.style.width = '100%'
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`
    }
  }
  lockCount.value += 1
}

export function unlockBodyScroll(): void {
  if (lockCount.value === 0) return
  lockCount.value -= 1
  if (lockCount.value > 0) return

  document.body.style.overflow = previousOverflow
  document.body.style.paddingRight = previousPaddingRight
  document.body.style.position = previousPosition
  document.body.style.top = previousTop
  document.body.style.width = previousWidth
  window.scrollTo(lockedScroll)
}
