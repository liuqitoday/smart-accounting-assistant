import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'

/** Refresh only the visible view, once on mount and once on each return. */
export function useDataRefresh(callback: () => void | Promise<void>): void {
  let active = false
  let pending = false
  let again = false
  let mounted = false
  async function refresh(): Promise<void> {
    if (!active) return
    if (pending) { again = true; return }
    pending = true
    try { await callback() } catch { /* The view presents its own retry state. */ }
    finally {
      pending = false
      if (again && active) { again = false; void refresh() }
    }
  }
  function activate(): void {
    if (active) return
    active = true
    void refresh()
  }
  onMounted(() => {
    mounted = true
    activate()
    window.addEventListener('app:data-changed', refresh)
    window.addEventListener('app:revalidated', refresh)
  })
  onActivated(() => { if (mounted) activate() })
  onDeactivated(() => { active = false; again = false })
  onBeforeUnmount(() => {
    active = false
    window.removeEventListener('app:data-changed', refresh)
    window.removeEventListener('app:revalidated', refresh)
  })
}
