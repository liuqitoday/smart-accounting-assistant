import { readonly, ref } from 'vue'

interface InstallPrompt extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}
const available = ref(false)
const installed = ref(false)
const instructions = ref(false)
let promptEvent: InstallPrompt | null = null
let listening = false

export function useInstall() {
  if (!listening) {
    listening = true
    installed.value = window.matchMedia('(display-mode: standalone)').matches || Boolean((navigator as Navigator & { standalone?: boolean }).standalone)
    window.addEventListener('beforeinstallprompt', event => {
      event.preventDefault()
      promptEvent = event as InstallPrompt
      available.value = true
    })
    window.addEventListener('appinstalled', () => { installed.value = true; available.value = false; instructions.value = false })
  }
  return {
    available: readonly(available), installed: readonly(installed), instructions,
    async install(): Promise<void> {
      if (!promptEvent) { instructions.value = true; return }
      await promptEvent.prompt()
      const choice = await promptEvent.userChoice
      if (choice.outcome === 'accepted') available.value = false
      promptEvent = null
    }
  }
}
