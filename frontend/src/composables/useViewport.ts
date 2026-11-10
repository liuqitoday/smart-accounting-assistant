import { onBeforeUnmount, onMounted } from 'vue'

export function useViewport(): void {
  function update(): void {
    const viewport = window.visualViewport
    const height = viewport?.height || window.innerHeight
    const offset = viewport?.offsetTop || 0
    const focused = document.activeElement?.matches('input,textarea,[contenteditable="true"]')
    const inset = focused ? Math.max(0, window.innerHeight - height - offset) : 0
    const root = document.documentElement
    root.style.setProperty('--visual-viewport-height', `${height}px`)
    root.style.setProperty('--visual-viewport-top', `${offset}px`)
    root.style.setProperty('--keyboard-inset', `${inset}px`)
    root.classList.toggle('keyboard-open', inset > 120)
  }
  onMounted(() => {
    update()
    window.visualViewport?.addEventListener('resize', update)
    window.visualViewport?.addEventListener('scroll', update)
    window.addEventListener('resize', update)
    document.addEventListener('focusin', update)
    document.addEventListener('focusout', update)
  })
  onBeforeUnmount(() => {
    window.visualViewport?.removeEventListener('resize', update)
    window.visualViewport?.removeEventListener('scroll', update)
    window.removeEventListener('resize', update)
    document.removeEventListener('focusin', update)
    document.removeEventListener('focusout', update)
  })
}
