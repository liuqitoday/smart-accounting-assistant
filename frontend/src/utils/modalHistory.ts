interface ModalEntry { id: string; close: () => void; path: string }
const stack: ModalEntry[] = []
let installed = false
let pendingClose: { path: string; parentId?: string } | null = null

export function registerModal(id: string, close: () => void): void {
  if (stack.some(entry => entry.id === id)) return
  if (!installed) {
    installed = true
    // Installed before the router's listener (see main.ts). A modal history
    // entry has the same URL, so consuming it must not trigger a route change.
    window.addEventListener('popstate', onPop, true)
  }
  stack.push({ id, close, path: location.pathname + location.search })
  history.pushState({ ...history.state, appModal: id }, '')
}

function onPop(event: PopStateEvent): void {
  const path = location.pathname + location.search
  const top = stack[stack.length - 1]
  if (pendingClose) {
    const expected = pendingClose
    pendingClose = null
    if ((!top || top.id === expected.parentId) && path === expected.path && event.state?.appModal === expected.parentId) {
      event.stopImmediatePropagation()
      return
    }
  }
  if (!top) return
  if (event.state?.appModal === top.id) return
  stack.pop()
  if (path === top.path) event.stopImmediatePropagation()
  top.close()
}

export function unregisterModal(id: string): void {
  const index = stack.findIndex(entry => entry.id === id)
  if (index < 0) return
  const wasTop = index === stack.length - 1
  const entry = stack[index]!
  stack.splice(index, 1)
  if (wasTop && history.state?.appModal === id) {
    pendingClose = { path: entry.path, parentId: stack[stack.length - 1]?.id }
    history.back()
  }
}

/** Close overlays before a deliberate route/ledger change. */
export function closeModalsForNavigation(): void {
  pendingClose = null
  const entries = stack.splice(0)
  // Do not issue an asynchronous back while a router push is in progress.
  if (history.state?.appModal) {
    const { appModal: _, ...state } = history.state
    history.replaceState(state, '')
  }
  for (const entry of entries.reverse()) entry.close()
}
