<template>
  <AppShell>
    <PageHeader
      title="智能记账"
      eyebrow="一句话,记一笔"
      subtitle="说出这笔账,剩下的交给 AI:金额、分类、账户自动帮你填好。"
    />

    <MessageBanner :message="message.text" :type="message.type" @dismissed="clear" />

    <section class="hero-card">
      <span class="hero-label"><CalendarDays class="hero-label-icon" />{{ heroPeriodLabel }}</span>
      <div class="hero-amount"><small>¥</small>{{ formatPlainMoney(summary?.totalExpense) }}</div>
      <div class="hero-sub">
        <span class="hero-sub-item">
          <span class="hero-sub-label">本月收入</span>
          <span class="hero-sub-value">¥{{ formatPlainMoney(summary?.totalIncome) }}</span>
        </span>
        <span class="hero-sub-item">
          <span class="hero-sub-label">结余</span>
          <span class="hero-sub-value">¥{{ formatPlainMoney(summary?.balance) }}</span>
        </span>
        <span class="hero-sub-item">
          <span class="hero-sub-label">笔数</span>
          <span class="hero-sub-value">{{ summary?.transactionCount || 0 }}</span>
        </span>
      </div>
    </section>

    <div class="grid cols-2 dashboard-grid">
      <section class="card input-card">
        <div class="form-field">
          <label for="transactionText">这笔账,怎么花的?</label>
          <textarea
            id="transactionText"
            ref="textareaRef"
            v-model.trim="text"
            class="textarea large-textarea"
            placeholder="例如:今天在星巴克买了一杯咖啡,花了35元"
            :disabled="!ledger.canEdit.value"
          />
        </div>
        <button class="button" type="button" :disabled="parsing || !text || !ledger.canEdit.value" @click="parse">
          <Sparkles />
          {{ parsing ? 'AI 正在帮你记...' : '解析这笔账' }}
        </button>
        <button class="button secondary" type="button" :disabled="!ledger.canEdit.value" @click="manualOpen = true">
          <Plus />
          手动记一笔
        </button>
        <p v-if="!ledger.canEdit.value" class="muted-text">当前账本为只读角色,不能新增交易。</p>
      </section>

      <section ref="resultCardRef" class="card result-card">
        <div class="result-head">
          <h2 class="panel-title">解析结果</h2>
          <div v-if="parseResult" class="result-meta">
            <span class="badge neutral">置信度 {{ confidenceText }}</span>
            <span v-if="parseResult.learnedFromHistory" class="badge neutral">命中历史习惯</span>
          </div>
        </div>
        <div v-if="!parseResult" class="empty-state sparkle">解析后会在这里展示,确认无误再保存</div>

        <div v-if="parseResult?.fallbackUsed" class="fallback-notice" role="status">
          <TriangleAlert aria-hidden="true" />
          <span>AI 暂时不可用，本次由基础规则解析。请重点核对金额、类型、日期和分类。</span>
        </div>

        <form v-if="parseResult" class="form-grid result-form" @submit.prevent="save">
          <div class="form-field">
            <label for="parsed-amount">金额</label>
            <input id="parsed-amount" v-model="parseResult.amount" class="input num" type="number" step="0.01" required />
          </div>
          <div class="form-field">
            <label for="parsed-type">类型</label>
            <select id="parsed-type" v-model="parseResult.type" class="select" @change="loadCategories">
              <option value="EXPENSE">支出</option>
              <option value="INCOME">收入</option>
            </select>
          </div>
          <div class="form-field">
            <label for="parsed-date">日期</label>
            <input id="parsed-date" v-model="parseResult.transactionDate" class="input" type="date" required />
          </div>
          <div class="form-field">
            <label for="parsed-account">账户</label>
            <select id="parsed-account" v-model="accountIdProxy" class="select">
              <option value="">未指定账户</option>
              <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
                {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
              </option>
            </select>
          </div>
          <div class="form-field full">
            <label for="parsed-category">分类</label>
            <CategorySelector
              input-id="parsed-category"
              v-model="parseResult.categoryId"
              :categories="flatCategories"
              :allow-empty="false"
              placeholder="请选择分类"
              search-placeholder="搜索分类名称或父级..."
            />
          </div>
          <div class="form-field full">
            <label for="parsed-description">描述</label>
            <input id="parsed-description" v-model.trim="parseResult.description" class="input" required />
          </div>
          <div class="form-field full">
            <label for="parsed-related-user">相关人员</label>
            <input id="parsed-related-user" v-model.trim="parseResult.relatedUser" class="input" placeholder="可选，例如 家人、同事或朋友" />
          </div>

          <div class="more-section full t-acc" :data-open="String(moreOpen)">
            <button
              id="parsed-extras-toggle"
              class="more-toggle full t-acc-head"
              type="button"
              aria-controls="parsed-extras-panel"
              :aria-expanded="moreOpen"
              @click="moreOpen = !moreOpen"
            >
              <span class="t-acc-chevron" aria-hidden="true"><ChevronDown /></span>
              补充信息(商户 / 备注 / 标签)
              <span v-if="extrasCount" class="badge neutral">已填 {{ extrasCount }} 项</span>
            </button>
            <div id="parsed-extras-panel" class="t-acc-panel" :aria-hidden="!moreOpen" :inert="moreOpen ? undefined : true">
              <div class="t-acc-panel-inner">
                <div class="more-fields">
                  <div class="form-field full">
                    <label for="parsed-merchant">商户</label>
                    <input id="parsed-merchant" v-model.trim="parseResult.parsedMerchant" class="input" placeholder="可选" />
                  </div>
                  <div class="form-field full">
                    <label for="parsed-note">备注</label>
                    <textarea id="parsed-note" v-model.trim="parseResult.note" class="textarea note-textarea" placeholder="可选" />
                  </div>
                  <div class="form-field full">
                    <span id="parsed-tags-label" class="field-label">标签</span>
                    <TagSelector labelledby="parsed-tags-label" :tags="tags" :selected-ids="selectedTagIds" @update:selected-ids="selectedTagIds = $event" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="form-field full save-row">
            <button class="button" type="submit" :disabled="saving || !ledger.canEdit.value">
              <span v-if="saving" class="t-shimmer button-shimmer" data-text="保存中...">保存中...</span>
              <template v-else>记下这一笔</template>
            </button>
          </div>
        </form>
      </section>
    </div>

    <ManualTransactionModal v-model="manualOpen" :accounts="accounts" :tags="tags" @saved="onManualSaved" />

    <Transition name="toast">
      <div v-if="showSavedToast" class="save-toast" role="status" aria-live="polite" aria-atomic="true">
        <span ref="savedCheckRef" class="t-success-check" :data-state="savedCheckState" aria-hidden="true">
          <CheckCircle2 />
        </span>
        已记下这一笔!
      </div>
    </Transition>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CalendarDays, CheckCircle2, ChevronDown, Plus, Sparkles, TriangleAlert } from 'lucide-vue-next'
import { accountsApi, categoriesApi, statisticsApi, tagsApi, transactionsApi } from '@/api'
import { flattenCategories } from '@/utils/category'
import AppShell from '@/components/AppShell.vue'
import CategorySelector from '@/components/CategorySelector.vue'
import ManualTransactionModal from '@/components/ManualTransactionModal.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import PageHeader from '@/components/PageHeader.vue'
import TagSelector from '@/components/TagSelector.vue'
import { useMessage } from '@/composables/useMessage'
import { useLedgerStore } from '@/stores/ledger'
import type { Account, Category, SaveTransactionRequest, StatisticsSummary, Tag, TransactionParseResponse } from '@/types'
import { defaultAccountIcon, formatPlainMoney, toNumber } from '@/utils/format'

const route = useRoute()
const ledger = useLedgerStore()
const text = ref('')
const parsing = ref(false)
const saving = ref(false)
const parseResult = ref<TransactionParseResponse | null>(null)
const categories = ref<Category[]>([])
const accounts = ref<Account[]>([])
const tags = ref<Tag[]>([])
const selectedTagIds = ref<number[]>([])
const summary = ref<StatisticsSummary | null>(null)
const showSavedToast = ref(false)
const manualOpen = ref(false)
const moreOpen = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const resultCardRef = ref<HTMLElement | null>(null)
const savedCheckRef = ref<HTMLElement | null>(null)
const savedCheckState = ref<'in' | 'out'>('out')
const { message, show, showSuccess, showError, clear } = useMessage()
let toastTimer: ReturnType<typeof setTimeout> | undefined

const flatCategories = computed(() => flattenCategories(categories.value))
const activeAccounts = computed(() => accounts.value.filter(account => account.active))
const confidenceText = computed(() => `${Math.round(toNumber(parseResult.value?.confidenceScore) * 100)}%`)
const heroPeriodLabel = computed(() => `${new Date().getMonth() + 1} 月支出`)
const extrasCount = computed(() => {
  if (!parseResult.value) return 0
  return [parseResult.value.parsedMerchant, parseResult.value.note, selectedTagIds.value.length].filter(Boolean).length
})
const accountIdProxy = computed({
  get: () => parseResult.value?.accountId ?? '',
  set: value => {
    if (parseResult.value) {
      parseResult.value.accountId = value === '' ? null : Number(value)
    }
  }
})

onMounted(async () => {
  focusIfRequested()
  await Promise.all([loadAccounts(), loadTags(), loadSummary()])
})

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
})

watch(
  () => route.query.focus,
  () => focusIfRequested()
)

function focusIfRequested(): void {
  if (route.query.focus) {
    textareaRef.value?.focus()
  }
}

async function parse(): Promise<void> {
  clear()
  parsing.value = true
  try {
    const result = await transactionsApi.parseOnly(text.value)
    parseResult.value = result
    selectedTagIds.value = result.tags?.map(tag => tag.id) || []
    moreOpen.value = false
    if (result.fallbackUsed) {
      show('info', 'AI 暂时不可用，已改用基础规则解析，请仔细核对结果。')
    } else {
      showSuccess('解析完成,确认无误后保存。')
    }
    // 分类加载失败会在 loadCategories 内落错误横幅,覆盖上面的成功提示
    await loadCategories()
    scrollToResultOnMobile()
  } catch (error) {
    showError(error)
  } finally {
    parsing.value = false
  }
}

async function save(): Promise<void> {
  if (!parseResult.value) return
  if (!parseResult.value.categoryId) {
    show('error', '请选择分类后再保存。')
    return
  }
  if (parseResult.value.type === 'TRANSFER') {
    show('error', '转账请使用手动记账中的转账功能。')
    return
  }
  saving.value = true
  clear()
  try {
    const payload: SaveTransactionRequest = {
      ...parseResult.value,
      type: parseResult.value.type
    }
    const saved = await transactionsApi.save(payload)
    if (saved.id && selectedTagIds.value.length) {
      await transactionsApi.updateTags(saved.id, selectedTagIds.value)
    }
    text.value = ''
    parseResult.value = null
    selectedTagIds.value = []
    celebrate()
    await loadSummary()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

function scrollToResultOnMobile(): void {
  // 移动端结果卡片在输入卡片下方,解析完成后滚过去,让用户立即看到结果
  if (!window.matchMedia('(max-width: 820px)').matches) return
  nextTick(() => {
    resultCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function celebrate(): void {
  showSavedToast.value = true
  replaySavedCheck()
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    showSavedToast.value = false
    savedCheckState.value = 'out'
  }, 2200)
}

function replaySavedCheck(): void {
  savedCheckState.value = 'out'
  void nextTick(() => {
    const check = savedCheckRef.value
    if (!check) return
    calibrateCheckPaths(check)
    void check.offsetWidth
    savedCheckState.value = 'in'
  })
}

function calibrateCheckPaths(check: HTMLElement): void {
  check.querySelectorAll<SVGGeometryElement>('svg path').forEach(path => {
    const length = Math.ceil(path.getTotalLength()) + 1
    path.style.strokeDasharray = String(length)
    path.style.strokeDashoffset = String(length)
  })
}

async function onManualSaved(): Promise<void> {
  celebrate()
  await Promise.all([loadSummary(), loadAccounts()])
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await statisticsApi.summary('current_month')
  } catch {
    // 看板加载失败不打断记账主流程
  }
}

async function loadCategories(): Promise<void> {
  if (!parseResult.value) return
  try {
    categories.value = await categoriesApi.list(parseResult.value.type)
  } catch (error) {
    showError(error, '分类加载失败,请稍后重试')
  }
}

async function loadAccounts(): Promise<void> {
  try {
    accounts.value = await accountsApi.list()
  } catch {
    // 看板加载失败不打断记账主流程
  }
}

async function loadTags(): Promise<void> {
  try {
    tags.value = await tagsApi.list()
  } catch {
    // 看板加载失败不打断记账主流程
  }
}
</script>

<style scoped>
.dashboard-grid {
  align-items: start;
}

.input-card {
  display: grid;
  gap: var(--space-md);
}

.large-textarea {
  min-height: 200px;
  font-size: 16px;
}

.hero-label-icon {
  width: 15px;
  height: 15px;
}

.result-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.result-head .panel-title {
  margin: 0;
}

.result-form {
  margin-top: var(--space-md);
}

.fallback-notice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: var(--space-md);
  border: 1px solid rgba(201, 138, 28, 0.34);
  border-radius: var(--radius-sm);
  background: rgba(201, 138, 28, 0.1);
  color: #79530f;
  padding: 11px 12px;
  font-size: 14px;
  line-height: 1.5;
}

.fallback-notice svg {
  flex: none;
  width: 18px;
  height: 18px;
  margin-top: 1px;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.more-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  padding: 2px 0;
  color: #82745a;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  justify-self: start;
}

.more-section {
  grid-column: 1 / -1;
}

.more-toggle .t-acc-chevron svg {
  width: 15px;
  height: 15px;
}

.more-fields {
  display: grid;
  gap: var(--space-md);
  padding-top: var(--space-md);
}

.note-textarea {
  min-height: 72px;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.92);
}

@media (max-width: 820px) {
  .large-textarea {
    min-height: 140px;
  }

  /* 短字段保持两列配对,压缩表单高度 */
  .result-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  /* 主操作吸底:悬浮在 Tabbar 上方,无需滚到表单底部 */
  .save-row {
    position: sticky;
    bottom: calc(72px + env(safe-area-inset-bottom));
    z-index: 5;
    margin: 0 calc(-1 * var(--space-sm));
    padding: var(--space-sm);
    border-radius: 999px;
    background: var(--color-surface);
    box-shadow: 0 6px 18px rgba(61, 48, 28, 0.14);
  }
}
</style>
