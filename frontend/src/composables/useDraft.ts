import { ref, watch, type Ref } from 'vue'
import { ledgerScope, readLocal, removeLocal, writeLocal } from '@/utils/localState'

export function useDraft<T extends object>(name: string, initial: T): { draft: Ref<T>; clear: () => void } {
  // Scope is intentionally captured: a late callback from an old ledger must
  // never write its draft into the newly selected ledger.
  const key = `draft:${ledgerScope()}:${name}`
  const defaults = () => JSON.parse(JSON.stringify(initial)) as T
  const stored = readLocal<{ value: T; savedAt: number }>(key)
  const draft = ref(stored && Date.now() - stored.savedAt < 30 * 86400000 ? { ...defaults(), ...stored.value } : defaults()) as Ref<T>
  watch(draft, value => writeLocal(key, { value, savedAt: Date.now() }), { deep: true, flush: 'sync' })
  return {
    draft,
    clear() {
      draft.value = defaults()
      removeLocal(key)
    }
  }
}
