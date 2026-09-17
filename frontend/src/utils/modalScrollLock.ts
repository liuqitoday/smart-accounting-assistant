let lockCount = 0
let previousOverflow = ''
let previousPaddingRight = ''

export function lockBodyScroll(): void {
  if (lockCount === 0) {
    previousOverflow = document.body.style.overflow
    previousPaddingRight = document.body.style.paddingRight
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
    document.body.style.overflow = 'hidden'
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`
    }
  }
  lockCount += 1
}

export function unlockBodyScroll(): void {
  if (lockCount === 0) return
  lockCount -= 1
  if (lockCount > 0) return

  document.body.style.overflow = previousOverflow
  document.body.style.paddingRight = previousPaddingRight
}
