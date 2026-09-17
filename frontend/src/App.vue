<template>
  <RouterView :key="ledgerKey" />
  <ConfirmModal />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { useLedgerStore } from '@/stores/ledger'

const ledger = useLedgerStore()
const ledgerKey = computed(() => ledger.state.activeLedger?.id ?? 'none')
</script>
