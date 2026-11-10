import { computed, reactive } from 'vue'
import { ledgerApi } from '@/api'
import { getActiveLedgerId, setActiveLedgerId } from '@/api/http'
import type { Ledger } from '@/types'
import { userScope } from '@/utils/localState'

const state = reactive({
  ledgers: [] as Ledger[],
  activeLedger: null as Ledger | null,
  loading: false,
  initialized: false,
  loadError: ''
})
let pendingLoad: Promise<Ledger | null> | null = null
let loadGeneration = 0

export function useLedgerStore() {
  const canEdit = computed(() => state.activeLedger?.myRole === 'OWNER' || state.activeLedger?.myRole === 'EDITOR')
  const isOwner = computed(() => state.activeLedger?.myRole === 'OWNER')

  async function performLoad(force = false): Promise<Ledger | null> {
    const generation = loadGeneration
    const owner = userScope()
    state.loading = true
    try {
      const ledgers = await ledgerApi.list(force)
      if (generation !== loadGeneration || owner !== userScope()) return null
      state.initialized = true
      state.loadError = ''
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
    } catch (error) {
      if (generation === loadGeneration && owner === userScope()) state.loadError = '账本暂时无法更新，请重新连接'
      throw error
    } finally {
      if (generation === loadGeneration && owner === userScope()) {
        state.loading = false
      }
    }
  }

  function load(force = false): Promise<Ledger | null> {
    if (pendingLoad) return pendingLoad
    const promise = performLoad(force).finally(() => { if (pendingLoad === promise) pendingLoad = null })
    pendingLoad = promise
    return pendingLoad
  }

  function switchTo(id: number): void {
    const target = state.ledgers.find(ledger => ledger.id === id)
    if (!target) return
    setActiveLedgerId(id)
    state.activeLedger = target
    window.dispatchEvent(new Event('app:ledger-changed'))
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
    loadGeneration += 1
    pendingLoad = null
    state.ledgers = []
    state.activeLedger = null
    state.loading = false
    state.initialized = false
    state.loadError = ''
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
