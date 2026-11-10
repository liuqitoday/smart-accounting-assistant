<template>
  <section class="text-composer">
    <p v-if="!online" class="message info">当前离线，可以先写下这笔账，或切换到手动记账。</p>
    <p v-if="error" class="message error" role="alert">{{ error }}</p>
    <div v-if="draft.result" class="composer-source-summary">
      <span class="composer-source-label">原文</span>
      <span class="composer-source-text" :title="draft.text">{{ draft.text }}</span>
      <button ref="sourceToggle" class="button ghost" type="button" :disabled="parsing || saving"
        :aria-expanded="sourceExpanded" aria-controls="composer-source-editor" @click="toggleSource">
        {{ sourceExpanded ? '收起' : '修改' }}
      </button>
    </div>
    <div id="composer-source-editor" v-show="showSourceEditor" ref="sourceEditor" class="composer-source-editor">
      <div class="form-field">
        <label for="transactionText">这笔账，怎么花的？</label>
        <textarea id="transactionText" ref="textInput" v-model="draft.text" class="textarea composer-input"
          placeholder="例如：午饭花了 28 元" :disabled="!canEdit || parsing || saving" />
      </div>
      <div class="composer-actions">
        <button class="button" type="button" :disabled="!canEdit || !online || parsing || saving || !draft.text.trim()" @click="parse">
          <Sparkles aria-hidden="true" />{{ parsing ? '正在解析…' : draft.result ? '重新解析' : '解析这笔账' }}
        </button>
        <button v-if="parsing" class="button secondary" type="button" @click="cancelParsing(true)">取消解析</button>
      </div>
    </div>
    <p v-if="parsing" class="muted-text" role="status">{{ parseStage }}</p>
    <p v-else-if="cancelled" class="muted-text" role="status">已取消解析，输入已保留。</p>
    <p v-if="sourceChanged && !parsing" class="muted-text composer-source-hint" role="status">原文已修改，重新解析后才会更新下方结果。</p>
    <template v-if="draft.result">
      <p v-if="draft.result.fallbackUsed" class="message info" role="status">AI 暂时不可用，已使用基础规则解析，请核对金额、日期和分类。</p>
      <form class="form-grid record-form composer-result" @submit.prevent="save">
        <fieldset class="composer-fields" :disabled="parsing || saving || !canEdit">
        <div class="form-field">
          <label for="parsed-amount">金额</label>
          <input id="parsed-amount" v-model="draft.result.amount" class="input num" type="number" inputmode="decimal" min="0.01" step="0.01" required />
        </div>
        <div class="form-field">
          <label for="parsed-type">类型</label>
          <select id="parsed-type" v-model="draft.result.type" class="select" @change="onTypeChange">
            <option value="EXPENSE">支出</option><option value="INCOME">收入</option>
          </select>
        </div>
        <div class="form-field">
          <label for="parsed-date">日期</label>
          <input id="parsed-date" v-model="draft.result.transactionDate" class="input" type="date" required />
        </div>
        <div class="form-field">
          <label for="parsed-account">账户</label>
          <select id="parsed-account" v-model="accountId" class="select">
            <option value="">未指定账户</option>
            <option v-for="account in activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
          </select>
        </div>
        <div class="form-field full">
          <label for="parsed-category">分类</label>
          <CategorySelector input-id="parsed-category" v-model="draft.result.categoryId" :categories="flatCategories"
            :allow-empty="false" placeholder="请选择分类" />
          <button v-if="categoryError" type="button" class="button ghost" @click="loadCategories">重新加载分类</button>
        </div>
        <div class="form-field full">
          <label for="parsed-description">描述</label>
          <input id="parsed-description" v-model.trim="draft.result.description" class="input" required />
        </div>
        <div class="t-acc full" :data-open="String(draft.moreOpen)">
          <button class="t-acc-head composer-more" type="button" :aria-expanded="draft.moreOpen" aria-controls="parsed-extras"
            @click="draft.moreOpen = !draft.moreOpen">
            <span>补充信息</span><span class="composer-more-hint">{{ extraCount ? `已填 ${extraCount} 项` : '商户、备注、标签' }}</span><ChevronDown class="t-acc-chevron" />
          </button>
          <div id="parsed-extras" class="t-acc-panel" :inert="draft.moreOpen ? undefined : true" :aria-hidden="!draft.moreOpen">
            <div class="t-acc-panel-inner"><div class="form-grid record-form composer-extras">
              <div class="form-field"><label for="parsed-merchant">商户</label><input id="parsed-merchant" v-model.trim="draft.result.parsedMerchant" class="input" /></div>
              <div class="form-field"><label for="parsed-related-user">相关人员</label><input id="parsed-related-user" v-model.trim="draft.result.relatedUser" class="input" /></div>
              <div class="form-field full"><label for="parsed-note">备注</label><textarea id="parsed-note" v-model.trim="draft.result.note" class="textarea" /></div>
              <div class="form-field full"><span id="parsed-tags-label" class="field-label">标签</span><TagSelector labelledby="parsed-tags-label" :tags="tags" :selected-ids="draft.tagIds" @update:selected-ids="draft.tagIds = $event" /></div>
            </div></div>
          </div>
        </div>
        </fieldset>
        <div class="form-field full record-save-actions"><button class="button" type="submit" :disabled="parsing || saving || !canEdit || categoryError">{{ saving ? '保存中…' : online ? '记下这一笔' : '保存，联网后同步' }}</button></div>
      </form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronDown, Sparkles } from 'lucide-vue-next'
import { categoriesApi, transactionsApi } from '@/api'
import CategorySelector from '@/components/CategorySelector.vue'
import TagSelector from '@/components/TagSelector.vue'
import { useDraft } from '@/composables/useDraft'
import { useConnectivity } from '@/composables/useConnectivity'
import { useLedgerStore } from '@/stores/ledger'
import { saveOrQueueTransaction } from '@/stores/outbox'
import { resolveDefaultAccountId } from '@/utils/account'
import { flattenCategories } from '@/utils/category'
import { identifySubmission, type SubmissionIdentity } from '@/utils/submission'
import type { AccountOption, Category, SaveTransactionRequest, Tag, TransactionParseResponse } from '@/types'

const props = defineProps<{ accounts: AccountOption[]; tags: Tag[]; active: boolean }>()
const emit = defineEmits<{ saved: [result: { queued: boolean }]; busy: [value: boolean] }>()
const ledger = useLedgerStore()
const ledgerId = ledger.state.activeLedger?.id
const { online } = useConnectivity()
const { draft, clear } = useDraft('text-transaction', {
  text: '', result: null as TransactionParseResponse | null, tagIds: [] as number[], moreOpen: false,
  submission: { fingerprint: '', key: '' } as SubmissionIdentity
})
const categories = ref<Category[]>([])
const categoryError = ref(false)
const parsing = ref(false)
const cancelled = ref(false)
const saving = ref(false)
const error = ref('')
const parseStage = ref('正在识别金额、日期和分类…')
const textInput = ref<HTMLTextAreaElement | null>(null)
const sourceEditor = ref<HTMLElement | null>(null)
const sourceToggle = ref<HTMLButtonElement | null>(null)
const sourceExpanded = ref(false)
const showSourceEditor = computed(() => !draft.value.result || sourceExpanded.value)
const sourceChanged = computed(() => !!draft.value.result && draft.value.text.trim() !== draft.value.result.originalText?.trim())
const extraCount = computed(() => {
  const result = draft.value.result
  return result ? [result.parsedMerchant, result.relatedUser, result.note, draft.value.tagIds.length].filter(Boolean).length : 0
})
let controller: AbortController | null = null
let stageTimer: ReturnType<typeof setTimeout> | undefined
const canEdit = computed(() => ledger.canEdit.value && ledger.state.activeLedger?.id === ledgerId)
const activeAccounts = computed(() => props.accounts.filter(account => account.active))
const flatCategories = computed(() => flattenCategories(categories.value))
const accountId = computed({
  get: () => draft.value.result?.accountId ?? '',
  set: value => { if (draft.value.result) draft.value.result.accountId = value === '' ? null : Number(value) }
})
watch(() => props.active, active => {
  if (active && showSourceEditor.value) void nextTick(() => textInput.value?.focus({ preventScroll: true }))
}, { immediate: true })
watch(() => draft.value.result?.type, () => { if (draft.value.result) void loadCategories() }, { immediate: true })
watch(saving, value => emit('busy', value))
watch(canEdit, value => { if (!value) cancelParsing() })
onBeforeUnmount(() => cancelParsing())

async function loadCategories(): Promise<void> {
  const type = draft.value.result?.type
  if (!type) return
  try {
    const result = await categoriesApi.list(type)
    if (draft.value.result?.type === type) { categories.value = result; categoryError.value = false }
  } catch { categoryError.value = true }
}
function onTypeChange(): void { if (draft.value.result) draft.value.result.categoryId = 0 }
function toggleSource(): void {
  sourceExpanded.value = !sourceExpanded.value
  void nextTick(() => (sourceExpanded.value ? textInput.value : sourceToggle.value)?.focus({ preventScroll: true }))
}
function cancelParsing(byUser = false): void {
  if (byUser) cancelled.value = true
  controller?.abort()
  controller = null
  parsing.value = false
  clearTimeout(stageTimer)
}
async function parse(): Promise<void> {
  if (!online.value || !canEdit.value || parsing.value || !draft.value.text.trim()) return
  const current = new AbortController()
  const text = draft.value.text.trim()
  controller = current
  let parsedSuccessfully = false
  parsing.value = true
  cancelled.value = false
  error.value = ''
  parseStage.value = '正在识别金额、日期和分类…'
  stageTimer = setTimeout(() => { parseStage.value = 'AI 还在处理，你可以取消并改为手动填写。' }, 7000)
  try {
    const result = await transactionsApi.parseOnly(text, current.signal)
    if (controller !== current || !canEdit.value) return
    draft.value.result = { ...result, originalText: text, accountId: result.accountId ?? resolveDefaultAccountId(props.accounts) }
    draft.value.tagIds = result.tags?.map(tag => tag.id) || []
    draft.value.submission = { fingerprint: '', key: '' }
    sourceExpanded.value = false
    parsedSuccessfully = true
    await loadCategories()
  } catch (failure) {
    if (controller === current && !current.signal.aborted) error.value = failure instanceof Error ? failure.message : '解析失败，请重试。'
  } finally {
    if (controller === current) {
      cancelParsing()
      if (parsedSuccessfully) void nextTick(() => {
        // 解析按钮禁用或编辑区收起后，浏览器可能已将焦点移到 body。
        // 用户切去别的页签或已开始核对字段时，不抢走焦点。
        if (props.active && (document.activeElement === document.body || sourceEditor.value?.contains(document.activeElement))) {
          sourceToggle.value?.focus({ preventScroll: true })
        }
      })
    }
  }
}
async function save(): Promise<void> {
  const result = draft.value.result
  if (!result || parsing.value || saving.value || !canEdit.value || !ledgerId) return
  if (!result.categoryId || result.type === 'TRANSFER' || !Number.isFinite(Number(result.amount)) || Number(result.amount) <= 0) {
    error.value = '请核对金额和分类后保存。'; return
  }
  const payload: SaveTransactionRequest = {
    amount: result.amount, type: result.type, description: result.description, originalText: result.originalText || draft.value.text,
    categoryId: result.categoryId, transactionDate: result.transactionDate, accountId: result.accountId ?? null,
    parsedMerchant: result.parsedMerchant, relatedUser: result.relatedUser, note: result.note,
    confidenceScore: result.confidenceScore, aiModelUsed: result.aiModelUsed, aiSuggestedCategoryId: result.aiSuggestedCategoryId,
    tagIds: draft.value.tagIds
  }
  saving.value = true
  error.value = ''
  try {
    const saved = await saveOrQueueTransaction(identifySubmission(payload, draft.value.submission), ledgerId)
    clear()
    if (ledger.state.activeLedger?.id === ledgerId) emit('saved', saved)
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '保存失败，请重试。' }
  finally { saving.value = false }
}
</script>

<style scoped>
.text-composer, .composer-source-editor { display: grid; gap: 12px; }
.composer-source-summary { display: flex; align-items: center; gap: 8px; min-width: 0; border-radius: var(--radius-sm); background: var(--color-surface-soft); padding-left: 12px; }
.composer-source-label { flex: none; font-size: 12px; color: var(--color-muted); }
.composer-source-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.composer-source-summary .button { flex: none; min-height: 44px; padding: 8px 12px; color: var(--color-primary-text); }
.composer-source-hint { margin: 0; font-size: 13px; }
.composer-input { min-height: 112px; font-size: 17px; }
.composer-actions { display: flex; gap: 10px; align-items: center; }
.composer-actions > .button:first-child { flex: 1; }
.composer-fields { display: contents; }
.composer-fields > .t-acc { grid-column: 1 / -1; }
.composer-more { display: flex; gap: 8px; align-items: center; width: 100%; min-height: 44px; padding: 8px 0; border: 0; background: transparent; color: var(--color-muted); text-align: left; }
.composer-more-hint { margin-left: auto; font-size: 12px; }
.composer-more svg { flex: none; width: 16px; height: 16px; }
.composer-extras { padding-top: 12px; }
</style>
