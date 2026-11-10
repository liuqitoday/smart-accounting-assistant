<template>
  <div class="analysis-view">
    <PageHeader title="AI 分析" subtitle="用自然语言提问，AI 帮你统计数据">
      <template #actions>
        <button v-if="messages.length" class="button secondary compact" :disabled="loading || clearing" @click="confirmClear">
          清空对话
        </button>
      </template>
    </PageHeader>

    <MessageBanner :message="banner.text" :type="banner.type" @dismissed="clearBanner" />

    <div class="analysis-container">
      <div v-if="historyError" class="history-error" role="alert"><span>聊天记录加载失败，已有对话和草稿已保留。</span><button class="button ghost compact" type="button" @click="loadFirstPage">重试加载</button></div>
      <div v-if="loadingHistory && !messages.length" class="history-skeleton" role="status" aria-label="正在加载聊天记录">
        <SkeletonBlock style="height: 48px; width: 65%; margin-left: auto" />
        <SkeletonBlock style="height: 100px; width: 85%" />
      </div>
      <!-- 空状态 -->
      <div v-if="showEmptyState" class="empty-state">
        <p class="empty-greeting">有什么想问的？试试这些：</p>
        <div class="example-chips">
          <button
            v-for="(ex, i) in examples"
            :key="i"
            class="chip-button"
            @click="askExample(ex)"
          >
            {{ ex }}
          </button>
        </div>
      </div>

      <!-- 消息流 -->
      <div v-else class="message-flow">
        <button
          v-if="hasMore"
          class="button ghost compact load-more"
          :disabled="loadingHistory"
          @click="loadMore"
        >
          查看更早的对话
        </button>
        <ChatMessage
          v-for="msg in messages"
          :key="msg.id"
          :message="msg"
          :data-message-id="msg.id"
          @follow-up="askQuestion"
          @open-transactions="openTransactions"
        />

        <!-- AI 占位气泡（加载中） -->
        <div v-if="loading" class="message assistant loading-placeholder">
          <div class="message-content">
            <span class="loading-text">{{ loadingPhase }}</span>
          </div>
        </div>

        <!-- 本地失败气泡的重试入口 -->
        <button v-if="canRetry" class="button ghost compact retry-button" @click="retryLast">
          重试上一问
        </button>
      </div>

      <!-- 输入框固定底部 -->
      <div class="input-bar">
        <div class="input-bar-inner">
          <textarea
            v-model="input"
            class="question-input"
            aria-label="分析问题"
            placeholder="输入你的问题..."
            rows="1"
            maxlength="500"
            @keydown="handleInputKeydown"
            @compositionstart="composing = true"
            @compositionend="composing = false"
          />
          <button
            class="button send"
            :disabled="!input.trim() || loading || clearing"
            @click="send"
          >
            发送
          </button>
        </div>
      </div>
    </div>

    <UiModal v-model="clearModalOpen" title="清空对话" subtitle="将删除所有历史消息">
      <div class="modal-body">
        <p>确定要清空吗？此操作不可恢复。</p>
      </div>
      <div class="modal-footer">
        <button class="button secondary" @click="clearModalOpen = false">取消</button>
        <button class="button danger" :disabled="clearing || loading" @click="executeClear">{{ clearing ? '清空中...' : '确认清空' }}</button>
      </div>
    </UiModal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { analysisApi } from '@/api'
import { ApiError } from '@/api/http'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import { useDataRefresh } from '@/composables/useDataRefresh'
import { useDraft } from '@/composables/useDraft'
import PageHeader from '@/components/PageHeader.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import UiModal from '@/components/UiModal.vue'
import ChatMessage from '@/components/analysis/ChatMessage.vue'
import { useMessage } from '@/composables/useMessage'
import type { AnalysisMessage } from '@/types'
import { parseAnalysisPayload } from '@/utils/analysisPayload'
import { defaultAnalysisExamples, toTransactionQuery, type TransactionDeepLinkFilters } from '@/utils/transactionQuery'

const PAGE_SIZE = 20

const router = useRouter()
const messages = ref<AnalysisMessage[]>([])
const { draft, clear: clearDraft } = useDraft('analysis-question', { question: '' })
const input = computed({ get: () => draft.value.question, set: value => { draft.value.question = value } })
const composing = ref(false)
const clearing = ref(false)
const historyError = ref(false)
let historyLoaded = false
let active = true
let destroyed = false
const loading = ref(false)
const loadingHistory = ref(false)
const loadingPhase = ref('AI 正在分析账本...')
const { message: banner, showSuccess, showError, clear: clearBanner } = useMessage()
const clearModalOpen = ref(false)
const page = ref(0)
const hasMore = ref(false)
const lastQuestion = ref('')
let localId = -1
let phaseTimers: number[] = []

const examples = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const message = messages.value[i]
    if (message.role !== 'ASSISTANT' || message.status !== 'OK') continue
    const parsed = parseAnalysisPayload(message.payload)
    if (parsed.version === 2 && parsed.payload.followUps.length) {
      return parsed.payload.followUps.map(item => item.question)
    }
    if (parsed.version === 1 && parsed.followUps.length) {
      return parsed.followUps
    }
  }
  return defaultAnalysisExamples()
})

const showEmptyState = computed(() => !messages.value.length && !loading.value && !loadingHistory.value)

/** 仅本地失败气泡（负 id）可重试；服务端持久化的 FAILED 消息重发即可 */
const canRetry = computed(() => {
  if (loading.value) return false
  const last = messages.value[messages.value.length - 1]
  return !!last && last.status === 'FAILED' && last.id < 0 && !!lastQuestion.value
})

useDataRefresh(async () => {
  if (!historyLoaded && !loadingHistory.value) await loadFirstPage()
})
onActivated(() => { active = true })
onDeactivated(() => { active = false })
onBeforeUnmount(() => {
  active = false
  destroyed = true
  clearPhaseTimers()
})

async function loadFirstPage(): Promise<void> {
  if (loadingHistory.value) return
  loadingHistory.value = true
  historyError.value = false
  try {
    const result = await analysisApi.messages(0, PAGE_SIZE)
    if (destroyed) return
    const history = [...result.content].reverse()
    const historyIds = new Set(history.map(item => item.id))
    const localOrNewer = messages.value.filter(item => item.id < 0 || !historyIds.has(item.id))
    messages.value = [...history, ...localOrNewer]
    page.value = 0
    hasMore.value = !result.last
    historyLoaded = true
    if (!loading.value) scrollToBottom()
  } catch (error) {
    historyError.value = true
    showError(error, '聊天记录加载失败')
  } finally {
    loadingHistory.value = false
  }
}

async function loadMore(): Promise<void> {
  if (loadingHistory.value || !hasMore.value) return
  loadingHistory.value = true
  try {
    const firstMessage = document.querySelector<HTMLElement>('[data-message-id]')
    const firstId = firstMessage?.dataset.messageId
    const firstTop = firstMessage?.getBoundingClientRect().top
    const result = await analysisApi.messages(page.value + 1, PAGE_SIZE)
    if (destroyed) return
    // 发新消息后服务端按页码分页会整体后移（offset 漂移），下一页可能重现已展示的消息：
    // 前插前按 id 去重，避免同一条消息渲染两次（key 重复）。本地失败气泡为负 id，不会与服务端 id 冲突。
    const existingIds = new Set(messages.value.map(item => item.id))
    const older = [...result.content].reverse().filter(item => !existingIds.has(item.id))
    messages.value = [...older, ...messages.value]
    page.value += 1
    hasMore.value = !result.last
    await nextTick()
    if (active && firstId && firstTop != null) {
      const anchor = document.querySelector<HTMLElement>(`[data-message-id="${firstId}"]`)
      if (anchor) window.scrollTo({ top: window.scrollY + anchor.getBoundingClientRect().top - firstTop })
    }
  } catch (error) {
    showError(error, '聊天记录加载失败')
  } finally {
    loadingHistory.value = false
  }
}

function handleInputKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.altKey || event.ctrlKey || event.metaKey) return
  if (event.isComposing || composing.value || event.keyCode === 229) return
  event.preventDefault()
  send()
}

function send(): void {
  const question = input.value.trim()
  if (!question || loading.value) return
  askQuestion(question)
}

function openTransactions(filters: TransactionDeepLinkFilters): void {
  void router.push({ name: 'transactions', query: toTransactionQuery(filters) })
}

function askExample(question: string): void {
  if (loading.value) return
  askQuestion(question)
}

function retryLast(): void {
  if (!canRetry.value) return
  // 失败时本地压入了 USER + FAILED ASSISTANT 一对气泡（均为负 id）：
  // 重试前成对移除，否则重试成功后同一问题会显示两遍。
  messages.value.pop()
  const previous = messages.value[messages.value.length - 1]
  if (previous && previous.id < 0 && previous.role === 'USER') {
    messages.value.pop()
  }
  askQuestion(lastQuestion.value)
}

function askQuestion(question: string): void {
  if (loading.value || clearing.value) return
  lastQuestion.value = question
  if (input.value.trim() === question) clearDraft()
  clearBanner()
  loading.value = true
  loadingPhase.value = 'AI 正在分析账本...'

  messages.value.push({
    id: localId--,
    role: 'USER',
    content: question,
    status: 'OK',
    createdAt: new Date().toISOString()
  })

  clearPhaseTimers()
  phaseTimers = [
    window.setTimeout(() => { if (loading.value) loadingPhase.value = '分析仍在继续，可以先查看其他页面。' }, 8000)
  ]
  scrollToBottom()

  void analysisApi.chat(question)
    .then(response => {
      if (destroyed) return
      replaceOptimisticUser(question, response.userMessage)
      messages.value.push(response.assistantMessage)
    })
    .catch((error: unknown) => {
      if (destroyed) return
      messages.value.push({
        id: localId--,
        role: 'ASSISTANT',
        content: classifyChatError(error),
        status: 'FAILED',
        createdAt: new Date().toISOString()
      })
    })
    .finally(() => {
      clearPhaseTimers()
      loading.value = false
      scrollToBottom()
    })
}

function replaceOptimisticUser(question: string, serverMessage: AnalysisMessage): void {
  const index = [...messages.value].reverse().findIndex(item => item.id < 0 && item.role === 'USER' && item.content === question)
  if (index < 0) {
    messages.value.push(serverMessage)
    return
  }
  messages.value.splice(messages.value.length - 1 - index, 1, serverMessage)
}

function classifyChatError(error: unknown): string {
  if (error instanceof ApiError) {
    const code = error.response?.errorCode
    if (code === 'RATE_LIMITED' || error.status === 429) return '达到分析次数上限'
    if (code === 'INVALID_PLAN') return '分析计划无法处理，请换个问法'
    if (code === 'QUERY_FAILED') return '查询失败，请稍后重试'
    if (error.status === 0 || error.status == null) return '网络异常，请稍后重试'
    return error.message || 'AI 服务暂时不可用，请稍后重试'
  }
  return '网络异常，请稍后重试'
}

function clearPhaseTimers(): void {
  phaseTimers.forEach(handle => window.clearTimeout(handle))
  phaseTimers = []
}

function scrollToBottom(): void {
  if (!active || destroyed) return
  void nextTick(() => {
    window.requestAnimationFrame(() => {
      if (active && !destroyed) window.scrollTo({ top: document.body.scrollHeight })
    })
  })
}

function confirmClear(): void {
  clearModalOpen.value = true
}

async function executeClear(): Promise<void> {
  if (loading.value || clearing.value) return
  clearing.value = true
  try {
    await analysisApi.clearMessages()
    messages.value = []
    page.value = 0
    hasMore.value = false
    clearModalOpen.value = false
    historyLoaded = true
    showSuccess('对话已清空')
  } catch (error) {
    showError(error, '清空失败')
  } finally {
    clearing.value = false
  }
}

defineExpose({ askQuestion })
</script>

<style scoped>
.history-error { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--color-danger-text); margin-bottom: 16px; }
.history-skeleton { display: grid; gap: 20px; padding: 20px 0; }
.analysis-container {
  max-width: 860px;
  margin: 0 auto;
  padding-bottom: 120px;
}

.empty-state {
  padding: 60px 20px;
  text-align: center;
}

.empty-greeting {
  font-size: 17px;
  color: var(--color-muted);
  margin-bottom: 24px;
}

.example-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.chip-button {
  padding: 10px 18px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.chip-button:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  transform: translateY(-1px);
}

.message-flow {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.loading-placeholder {
  align-self: flex-start;
  max-width: 75%;
  padding: 14px 18px;
  border-radius: var(--radius-md);
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border-soft);
}

.loading-text {
  color: var(--color-muted);
  font-size: 14px;
}

.input-bar {
  position: fixed;
  bottom: var(--keyboard-inset, 0px);
  left: 248px; /* 与 .app-shell 侧栏列宽同步（styles.css grid-template-columns: 248px …） */
  right: 0;
  padding: 16px 20px calc(16px + env(safe-area-inset-bottom));
  background: rgba(255, 253, 248, 0.96);
  backdrop-filter: blur(16px);
  border-top: 1px solid var(--color-border);
  z-index: 30;
}

/* 内容器限宽居中：与聊天列（.analysis-container 的 860px）对齐 */
.input-bar-inner {
  display: flex;
  gap: 12px;
  max-width: 860px;
  margin: 0 auto;
}

@media (max-width: 820px) {
  .input-bar {
    left: 0;
    /* 紧贴 tabbar 顶缘。tabbar 实高 = 1px 边框 + 上下 6px 内边距 + 项高
       （6px + 22px 图标 + 3px 间距 + 12px 文字行高 + 4px ≈ 49~52px）≈ 62~65px + safe-area。
       取 60px 让输入栏下缘略垫入 tabbar 之下（输入栏 z-30 < tabbar z-40，被其近不透明底盖住），
       任何字体度量下都不再漏缝；中央记账 FAB 顶缘位于底部 58px + safe-area 处
       （52px 高、锚定在 6px 底内边距上，未越出 tabbar 顶缘），始终低于输入栏下缘，不会压住输入栏。 */
    bottom: calc(var(--keyboard-inset, 0px) + var(--mobile-tabbar-height, 60px) + env(safe-area-inset-bottom));
    /* 非贴底摆放：safe-area 由下方 tabbar 承担，无需重复垫高 */
    padding-bottom: 16px;
  }

  .analysis-container {
    padding-bottom: 160px;
  }

  html.keyboard-open .input-bar {
    bottom: var(--keyboard-inset, 0px);
    padding-bottom: 10px;
  }
}

.question-input {
  flex: 1;
  min-height: 42px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 16px;
  resize: none;
  max-height: min(120px, calc(var(--visual-viewport-height, 100dvh) * 0.28));
}

.load-more,
.retry-button {
  align-self: center;
}

.modal-body p {
  margin: 0 0 4px;
  color: var(--color-text);
  font-size: 14px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
</style>
