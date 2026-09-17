<template>
  <div class="message" :class="isUser ? 'user' : 'assistant'">
    <div class="message-content" :class="{ failed: message.status === 'FAILED' }">
      <p class="message-text">{{ message.content }}</p>
      <p v-if="!isUser && parseFailed" class="parse-error">结果无法解析</p>
      <ul v-if="!isUser && warnings.length" class="warning-list">
        <li v-for="(warning, i) in warnings" :key="i">{{ warning }}</li>
      </ul>
      <p v-if="!isUser && statusNote" class="status-note">{{ statusNote }}</p>
      <template v-if="!isUser && cards.length">
        <ResultCard
          v-for="(card, i) in cards"
          :key="i"
          :card="card"
          @open-transactions="emit('open-transactions', $event)"
        />
      </template>
      <div v-if="!isUser && followUps.length" class="follow-ups">
        <button
          v-for="item in followUps"
          :key="item.question"
          type="button"
          class="follow-up"
          @click="emit('follow-up', item.question)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ResultCard from '@/components/analysis/ResultCard.vue'
import type { AnalysisFollowUp, AnalysisMessage, AnalysisQueryResult, AnalysisResultV2 } from '@/types'
import { parseAnalysisPayload } from '@/utils/analysisPayload'
import type { TransactionDeepLinkFilters } from '@/utils/transactionQuery'

const props = defineProps<{ message: AnalysisMessage }>()
const emit = defineEmits<{
  'follow-up': [question: string]
  'open-transactions': [filters: TransactionDeepLinkFilters]
}>()

const isUser = computed(() => props.message.role === 'USER')

const parsed = computed(() => parseAnalysisPayload(props.message.payload))

const parseFailed = computed(() => parsed.value.version === 'invalid')

const cards = computed<Array<AnalysisQueryResult | AnalysisResultV2>>(() => {
  const result = parsed.value
  if (result.version === 1) return result.results
  if (result.version === 2) return result.payload.results
  return []
})

const followUps = computed<AnalysisFollowUp[]>(() => {
  const result = parsed.value
  if (result.version === 2) return result.payload.followUps
  if (result.version === 1) {
    return result.followUps.map(question => ({ label: question, question }))
  }
  return []
})

const warnings = computed(() => {
  const result = parsed.value
  if (result.version === 2) return result.payload.warnings.map(warning => warning.message)
  if (result.version === 1) return result.warnings
  return result.warnings
})

const statusNote = computed(() => {
  const result = parsed.value
  if (result.version !== 2) return ''
  if (result.payload.status === 'NO_DATA') return '该时间段没有符合条件的数据'
  if (result.payload.status === 'PARTIAL_RESULT') return '部分结果已省略或截断'
  return ''
})
</script>

<style scoped>
.message { display: flex; }
.message.user { justify-content: flex-end; }
.message.assistant { justify-content: flex-start; }

.message-content {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border-soft);
  box-shadow: var(--shadow-card);
}

.message.user .message-content {
  /* 白字压 primary(#eb5e28) 仅 3.4:1、压 primary-dark(#d14c1d) 4.4:1 仍差一线，
     再混 8% 黑（≈#c0461b）达到 ≥4.5:1（WCAG AA 小字） */
  background: color-mix(in srgb, var(--color-primary-dark) 92%, #000);
  color: #fff;
  border: none;
}

.message-content.failed { border-color: var(--color-danger); }
.message-content.failed .message-text { color: var(--color-danger-text); }

.message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 15px;
  line-height: 1.55;
}

.parse-error {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--color-danger-text);
}

.warning-list,
.status-note {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: var(--color-muted);
}

.follow-ups {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.follow-up {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
}

.follow-up:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}
</style>
