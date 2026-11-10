<template>
  <UiModal keep-mounted :model-value="recording.state.open" title="记一笔" size="lg" @update:model-value="requestClose">
    <div class="record-sheet">
      <div class="pill-tabs record-modes" role="tablist" aria-label="记账方式">
        <button v-for="mode in modes" :id="`record-tab-${mode.value}`" :key="mode.value" type="button" role="tab"
          :class="{ active: recording.state.mode === mode.value }" :aria-selected="recording.state.mode === mode.value"
          :aria-controls="`record-panel-${mode.value}`" :tabindex="recording.state.mode === mode.value ? 0 : -1" :disabled="busy" @keydown="onModeKeydown" @click="recording.state.mode = mode.value">
          <component :is="mode.icon" aria-hidden="true" />{{ mode.label }}
        </button>
      </div>
      <p v-if="!ledger.canEdit.value" class="message info">当前账本为只读，无法新增交易。</p>
      <p v-if="resourceError" class="message error" role="alert">{{ resourceError }} <button class="button ghost" type="button" @click="loadOptions">重试</button></p>
      <div id="record-panel-text" v-show="recording.state.mode === 'text'" role="tabpanel" aria-labelledby="record-tab-text">
        <TransactionComposer :accounts="accounts" :tags="tags" :active="recording.state.open && recording.state.mode === 'text'" @busy="busy = $event" @saved="onSaved($event, true)" />
      </div>
      <div id="record-panel-manual" v-show="recording.state.mode === 'manual'" role="tabpanel" aria-labelledby="record-tab-manual">
        <ManualTransactionModal embedded :model-value="recording.state.open && recording.state.mode === 'manual'" :accounts="accounts" :tags="tags" @busy="busy = $event" @saved="onSaved($event, true)" />
      </div>
      <div v-if="capabilities?.imageParsingEnabled" id="record-panel-image" v-show="recording.state.mode === 'image'" role="tabpanel" aria-labelledby="record-tab-image">
        <ImageTransactionModal embedded :model-value="recording.state.open && recording.state.mode === 'image'" :capabilities="capabilities" :accounts="accounts" @busy="busy = $event" @saved="onSaved($event, false)" @update:model-value="requestClose" />
      </div>
      <p v-else-if="recording.state.mode === 'image'" class="message info">截图识别暂不可用，可以使用文字或手动记账。</p>
    </div>
  </UiModal>
  <Transition name="toast"><div v-if="recording.state.notice" class="record-notice" role="status" aria-live="polite"><CheckCircle2 aria-hidden="true" />{{ recording.state.notice }}</div></Transition>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { CheckCircle2, ImagePlus, PencilLine, Sparkles } from 'lucide-vue-next'
import UiModal from '@/components/UiModal.vue'
import ManualTransactionModal from '@/components/ManualTransactionModal.vue'
import ImageTransactionModal from '@/components/ImageTransactionModal.vue'
import TransactionComposer from '@/components/TransactionComposer.vue'
import { accountsApi, tagsApi, transactionsApi } from '@/api'
import { useRecording, type RecordingMode } from '@/stores/recording'
import { useLedgerStore } from '@/stores/ledger'
import type { AccountOption, Tag, TransactionCapabilities } from '@/types'

const recording = useRecording()
const ledger = useLedgerStore()
const accounts = ref<AccountOption[]>([])
const tags = ref<Tag[]>([])
const capabilities = ref<TransactionCapabilities | null>(null)
const resourceError = ref('')
const busy = ref(false)
const modes = computed(() => [
  { value: 'text' as RecordingMode, label: '文字', icon: Sparkles },
  { value: 'manual' as RecordingMode, label: '手动', icon: PencilLine },
  ...(capabilities.value?.imageParsingEnabled ? [{ value: 'image' as RecordingMode, label: '截图', icon: ImagePlus }] : [])
])
watch(() => recording.state.open, open => { if (open) void loadOptions() }, { immediate: true })
onBeforeUnmount(() => recording.close())
async function loadOptions(): Promise<void> {
  resourceError.value = ''
  const results = await Promise.allSettled([accountsApi.options(), tagsApi.list(), transactionsApi.capabilities()])
  if (results[0].status === 'fulfilled') accounts.value = results[0].value
  if (results[1].status === 'fulfilled') tags.value = results[1].value
  if (results[2].status === 'fulfilled') capabilities.value = results[2].value
  if (results[0].status === 'rejected' || results[1].status === 'rejected') resourceError.value = '部分选项未能加载，已有草稿仍然保留。'
}
function onModeKeydown(event: KeyboardEvent): void {
  if (busy.value || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const options = modes.value
  const index = options.findIndex(mode => mode.value === recording.state.mode)
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
    : (index + (event.key === 'ArrowRight' ? 1 : -1) + options.length) % options.length
  recording.state.mode = options[next].value
  const buttons = (event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')
  buttons?.[next]?.focus()
}
function requestClose(value: boolean): void { recording.state.open = value }
function onSaved(result: { queued: boolean } | undefined, close: boolean): void {
  recording.notifySaved(result?.queued)
  if (close) recording.close()
}
</script>

<style scoped>
.record-sheet { display: grid; gap: 12px; }
.record-modes { display: flex; width: 100%; }
.record-modes button { display: inline-flex; flex: 1; justify-content: center; align-items: center; gap: 7px; min-width: 0; min-height: 44px; padding: 8px 10px; white-space: nowrap; }
.record-modes svg { flex: none; width: 18px; height: 18px; }
.record-notice { position: fixed; z-index: 1500; left: 50%; bottom: calc(94px + env(safe-area-inset-bottom)); transform: translateX(-50%); display: flex; align-items: center; gap: 9px; width: max-content; max-width: calc(100vw - 32px); padding: 13px 18px; color: var(--color-text); background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); box-shadow: var(--shadow-soft); }
.record-notice svg { width: 20px; height: 20px; color: var(--color-success); }
</style>
