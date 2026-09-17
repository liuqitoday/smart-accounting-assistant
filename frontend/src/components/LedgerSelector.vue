<template>
  <select class="ledger-select" aria-label="切换账本" :value="activeId" :disabled="ledger.state.loading" @change="onChange">
    <option v-for="item in ledger.state.ledgers" :key="item.id" :value="item.id">
      {{ item.icon || '📒' }} {{ item.name }}{{ item.myRole === 'OWNER' ? '' : `（${roleLabel(item.myRole)}）` }}
    </option>
  </select>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useLedgerStore } from '@/stores/ledger'
import { roleLabel } from '@/utils/format'

const ledger = useLedgerStore()
const activeId = computed(() => ledger.state.activeLedger?.id || '')

async function onChange(event: Event): Promise<void> {
  const value = Number((event.target as HTMLSelectElement).value)
  if (value) {
    await ledger.switchTo(value)
  }
}
</script>
