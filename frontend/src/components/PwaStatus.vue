<template>
  <aside v-if="showUpdate" class="pwa-update" role="status">
    <span>新版本已准备好，草稿会保留。</span>
    <button type="button" class="banner-action-btn" :disabled="pendingOperations > 0" @click="update">{{ pendingOperations > 0 ? '操作完成后可更新' : '更新' }}</button>
    <button type="button" class="banner-action-btn" @click="needRefresh = false">稍后</button>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { isModalScrollLocked } from '@/utils/modalScrollLock'
import { useRegisterSW } from 'virtual:pwa-register/vue'
import { useRequestActivity } from '@/api/http'
const { pendingOperations } = useRequestActivity()
const { needRefresh, updateServiceWorker } = useRegisterSW()
const showUpdate = computed(() => needRefresh.value && !isModalScrollLocked.value)
async function update(): Promise<void> {
  // Defer while an explicit save/parse is in progress. Drafts are synchronous
  // and survive reload, while in-flight requests need time to settle.
  if (pendingOperations.value || isModalScrollLocked.value) return
  await updateServiceWorker(true)
}
</script>

<style scoped>
.pwa-update { position:fixed; z-index:150; left:50%; transform:translateX(-50%); bottom:calc(90px + env(safe-area-inset-bottom)); width:max-content; max-width:calc(100vw - 28px); display:flex; flex-wrap:wrap; gap:8px; padding:12px 18px; border-radius:var(--radius-md); background:var(--color-surface); border:1px solid var(--color-border); box-shadow:var(--shadow-card); font-size:14px; }
</style>
