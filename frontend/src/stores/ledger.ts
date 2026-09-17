import { computed, reactive } from 'vue'
import { ledgerApi } from '@/api'
import { getActiveLedgerId, setActiveLedgerId } from '@/api/http'
import type { Ledger } from '@/types'

const state = reactive({
  ledgers: [] as Ledger[],
  activeLedger: null as Ledger | null,
  loading: false,
  initialized: false
})

export function useLedgerStore() {
  const canEdit = computed(() => state.activeLedger?.myRole === 'OWNER' || state.activeLedger?.myRole === 'EDITOR')
  const isOwner = computed(() => state.activeLedger?.myRole === 'OWNER')

  async function load(): Promise<Ledger | null> {
    state.loading = true
    try {
      const ledgers = await ledgerApi.list()
      state.ledgers = ledgers

      if (!ledgers.length) {
        state.activeLedger = null
        setActiveLedgerId('')
        return null
      }

      const storedId = getActiveLedgerId()
      const stored = ledgers.find(ledger => String(ledger.id) === storedId)
      const fallback = ledgers.find(ledger => ledger.default) || ledgers[0]
      const active = stored || fallback

      state.activeLedger = active
      setActiveLedgerId(active.id)
      return active
    } finally {
      state.loading = false
      state.initialized = true
    }
  }

  function switchTo(id: number): void {
    setActiveLedgerId(id)
    state.activeLedger = state.ledgers.find(ledger => ledger.id === id) || state.activeLedger
  }

  async function setDefault(id: number): Promise<void> {
    await ledgerApi.setDefault(id)
    const activeId = state.activeLedger?.id
    await load()
    if (activeId && activeId !== state.activeLedger?.id) {
      const active = state.ledgers.find(ledger => ledger.id === activeId)
      if (active) {
        state.activeLedger = active
        setActiveLedgerId(active.id)
      }
    }
  }

  function reset(): void {
    state.ledgers = []
    state.activeLedger = null
    state.loading = false
    state.initialized = false
    setActiveLedgerId('')
  }

  return {
    state,
    canEdit,
    isOwner,
    load,
    switchTo,
    setDefault,
    reset
  }
}
