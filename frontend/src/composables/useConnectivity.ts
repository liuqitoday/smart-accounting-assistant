import { readonly, ref } from 'vue'

const online = ref(typeof navigator === 'undefined' || navigator.onLine)
const cached = ref(false)
let installed = false

export function setConnectionAvailable(value: boolean): void {
  online.value = value
  if (value) cached.value = false
}

export function markCachedData(): void { cached.value = true }

export function useConnectivity() {
  if (!installed && typeof window !== 'undefined') {
    installed = true
    window.addEventListener('offline', () => { online.value = false })
    window.addEventListener('online', () => {
      online.value = true
      window.dispatchEvent(new Event('app:reconnect'))
    })
  }
  return { online: readonly(online), cached: readonly(cached) }
}
