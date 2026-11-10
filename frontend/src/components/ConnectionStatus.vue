<template>
  <section v-if="!online || cached || auth.state.offlineSession || ledger.state.loadError || outbox.entries.value.length" class="connection-status" role="status">
    <span>{{ statusText }}</span>
    <button v-if="online" class="banner-action-btn" type="button" :disabled="checking || outbox.syncing.value" @click="retry">
      {{ checking || outbox.syncing.value ? '正在同步…' : '重新连接' }}
    </button>
    <button v-if="outbox.entries.value.length" class="banner-action-btn" type="button" @click="pendingOpen = true">查看待同步</button>
  </section>
  <UiModal v-model="pendingOpen" title="待同步账目" subtitle="已保存在这台设备。联网并确认登录后，会自动同步到原账本。">
    <ul class="pending-list">
      <li v-for="entry in outbox.entries.value" :key="entry.id">
        <div><strong>{{ entry.payload.description }}</strong><span>¥{{ entry.payload.amount }} · {{ entry.payload.transactionDate }}</span></div>
        <p v-if="entry.error" class="pending-error">{{ entry.error }}</p>
        <button v-if="entry.error" type="button" class="button ghost compact" @click="discard(entry.id)">移除此条</button>
      </li>
    </ul>
    <p v-if="!outbox.entries.value.length" class="empty-state">账目已全部同步</p>
    <button class="button" type="button" :disabled="!online || outbox.syncing.value" @click="outbox.sync(true)">{{ outbox.syncing.value ? '同步中…' : '重试同步' }}</button>
  </UiModal>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import UiModal from '@/components/UiModal.vue'
import { useConnectivity } from '@/composables/useConnectivity'
import { useAuthStore } from '@/stores/auth'
import { useLedgerStore } from '@/stores/ledger'
import { useOutbox } from '@/stores/outbox'
import { useConfirm } from '@/composables/useConfirm'
import { expireQueries } from '@/api/queryCache'

const { online, cached } = useConnectivity()
const auth = useAuthStore()
const ledger = useLedgerStore()
const outbox = useOutbox()
const pendingOpen = ref(false)
const checking = ref(false)
const statusText = computed(() => !online.value ? '当前离线 · 显示本机最近的数据，草稿会自动保留'
  : auth.state.offlineSession ? '正在使用本机数据，连接后将验证登录状态'
    : ledger.state.loadError || (outbox.entries.value.length ? `${outbox.entries.value.length} 笔账目待同步` : '暂时无法更新，正在显示上次的数据'))

async function retry(): Promise<void> {
  if (checking.value) return
  checking.value = true
  try {
    await auth.initialize(true)
    if (!auth.isAuthenticated.value || auth.state.offlineSession) return
    expireQueries()
    await ledger.load(true)
    await outbox.sync()
    window.dispatchEvent(new Event('app:revalidated'))
  } catch { /* Keep local data and the visible retry action. */ }
  finally { checking.value = false }
}
function handleReconnect(): void { void retry() }
function onVisible(): void { if (document.visibilityState === 'visible' && navigator.onLine) void retry() }
async function discard(id: string): Promise<void> {
  if (await useConfirm().confirm({ title: '移除待同步账目', message: '这条账目未能同步，移除后将不会继续重试。', danger: true, confirmText: '移除' })) outbox.discard(id)
}
onMounted(() => {
  window.addEventListener('app:reconnect', handleReconnect)
  window.addEventListener('app:access-changed', handleReconnect)
  document.addEventListener('visibilitychange', onVisible)
  if (online.value && outbox.entries.value.length) void retry()
})
onBeforeUnmount(() => {
  window.removeEventListener('app:reconnect', handleReconnect)
  window.removeEventListener('app:access-changed', handleReconnect)
  document.removeEventListener('visibilitychange', onVisible)
})
</script>

<style scoped>
.connection-status { display:flex; flex-wrap:wrap; gap:8px; align-items:center; margin-bottom:16px; padding:10px 14px; border:1px solid var(--color-border); border-radius:var(--radius-sm); background:var(--color-surface-soft); font-size:13px; }
.pending-list { list-style:none; padding:0; }
.pending-list li { padding:14px 0; border-bottom:1px solid var(--color-border); }
.pending-list li div { display:flex; flex-wrap:wrap; gap:8px; justify-content:space-between; }
.pending-error { color:var(--color-danger-text); font-size:14px; }
</style>
