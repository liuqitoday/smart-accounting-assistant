<template>
  <main v-if="!auth.state.initialized" class="app-starting" aria-busy="true" aria-label="正在打开账本">
    <p>正在打开账本…</p>
    <SkeletonBlock style="height: 140px" />
    <SkeletonBlock style="height: 220px" />
  </main>
  <RouterView v-else v-slot="{ Component, route }">
    <component :is="Component" v-if="route.meta.public" />
    <AppShell v-else :key="ledgerKey">
      <ConnectionStatus />
      <KeepAlive :max="8">
        <component :is="Component" :key="route.name" />
      </KeepAlive>
      <RecordSheet />
    </AppShell>
  </RouterView>
  <ConfirmModal />
  <PwaStatus />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, watch } from 'vue'
import AppShell from '@/components/AppShell.vue'
import ConnectionStatus from '@/components/ConnectionStatus.vue'
import PwaStatus from '@/components/PwaStatus.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { useLedgerStore } from '@/stores/ledger'
import { useAuthStore } from '@/stores/auth'
import { useNavigation } from '@/stores/navigation'
import { useViewport } from '@/composables/useViewport'
import { closeModalsForNavigation } from '@/utils/modalHistory'
import { useInstall } from '@/composables/useInstall'

const RecordSheet = defineAsyncComponent(() => import('@/components/RecordSheet.vue'))

const ledger = useLedgerStore()
const auth = useAuthStore()
const ledgerKey = computed(() => `${auth.state.username}:${ledger.state.activeLedger?.id ?? 'none'}`)
useViewport()
useInstall()
watch(ledgerKey, () => { useNavigation().reset(); closeModalsForNavigation() })
</script>

<style scoped>
.app-starting { max-width:720px; margin:auto; padding:40px 18px; display:grid; gap:24px; }
.app-starting p { color:var(--color-muted); }
</style>
