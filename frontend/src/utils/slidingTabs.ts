import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export function useSlidingTabs(selectIndex?: (index: number) => void) {
  const tabsRef = ref<HTMLElement | null>(null)
  let resizeHandler: (() => void) | undefined

  function moveToActive(animate: boolean): void {
    const bar = tabsRef.value
    if (!bar) return

    const pill = bar.querySelector<HTMLElement>('.t-tabs-pill')
    const activeTab =
      bar.querySelector<HTMLElement>('.t-tab[aria-selected="true"]') || bar.querySelector<HTMLElement>('.t-tab')
    if (!pill || !activeTab) return

    if (!animate) {
      const previousTransition = pill.style.transition
      pill.style.transition = 'none'
      pill.style.transform = `translateX(${activeTab.offsetLeft}px)`
      pill.style.width = `${activeTab.offsetWidth}px`
      void pill.offsetWidth
      pill.style.transition = previousTransition
      return
    }

    pill.style.transform = `translateX(${activeTab.offsetLeft}px)`
    pill.style.width = `${activeTab.offsetWidth}px`
  }

  function syncTabs(animate = true): void {
    void nextTick(() => {
      window.requestAnimationFrame(() => moveToActive(animate))
    })
  }

  function handleTabKeydown(event: KeyboardEvent): void {
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
    const tabs = Array.from(tabsRef.value?.querySelectorAll<HTMLElement>('[role="tab"]:not([disabled])') ?? [])
    if (!tabs.length) return

    const currentIndex = Math.max(0, tabs.indexOf(event.currentTarget as HTMLElement))
    let nextIndex = currentIndex
    if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = tabs.length - 1
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + tabs.length) % tabs.length
    else nextIndex = (currentIndex + 1) % tabs.length

    event.preventDefault()
    selectIndex?.(nextIndex)
    void nextTick(() => {
      const currentTabs = Array.from(tabsRef.value?.querySelectorAll<HTMLElement>('[role="tab"]:not([disabled])') ?? [])
      currentTabs[nextIndex]?.focus({ preventScroll: true })
    })
  }

  onMounted(() => {
    syncTabs(false)
    resizeHandler = () => syncTabs(false)
    window.addEventListener('resize', resizeHandler)
  })

  onBeforeUnmount(() => {
    if (resizeHandler) {
      window.removeEventListener('resize', resizeHandler)
    }
  })

  return {
    tabsRef,
    syncTabs,
    handleTabKeydown
  }
}
