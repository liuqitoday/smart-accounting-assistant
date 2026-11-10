<template>
  <component
    :is="embedded ? 'div' : UiModal"
    :model-value="modelValue"
    title="截图记账"
    subtitle="上传消费或收入截图，核对识别结果后逐笔入账。"
    size="xl"
    @update:model-value="requestClose"
  >
    <div class="image-accounting" @paste="onPaste">
      <p v-if="!online" class="message info">当前离线，已有识别结果仍可保存；识别新截图需要联网。</p>
      <p v-if="errorText" class="message error" role="alert">{{ errorText }}</p>
      <p v-if="!canEdit" class="message info">当前账本为只读角色，不能识别或保存交易。</p>
      <div class="image-workspace">
        <section class="image-source" aria-label="原始截图">
          <input
            ref="fileInput"
            class="image-file-input"
            type="file"
            hidden
            :accept="capabilities.supportedImageTypes.join(',')"
            aria-label="选择消费或收入截图"
            :disabled="busy || !canEdit"
            @change="onFileChange"
          />
          <div class="image-dropzone" :class="{ 'has-preview': previewUrl }" @dragover.prevent @drop.prevent="onDrop">
            <img v-if="previewUrl" :src="previewUrl" alt="待识别的消费或收入截图" class="image-preview" />
            <template v-else>
              <ImagePlus class="image-placeholder-icon" aria-hidden="true" />
              <strong>把这笔账放进来</strong>
              <p>消费小票、支付详情或收支明细<br />也可以拖入或粘贴截图</p>
            </template>
            <button class="button secondary" type="button" :disabled="busy || !canEdit" @click="fileInput?.click()">
              <Upload aria-hidden="true" />{{ selectedFile ? '重新选择图片' : '选择图片' }}
            </button>
          </div>
          <p v-if="selectedFile" class="image-file-name">{{ selectedFile.name }}</p>
          <p class="muted-text image-file-help">PNG / JPG，单张不超过 {{ sizeLimitMb }} MB，最多识别 {{ capabilities.maxTransactionsPerImage }} 笔。</p>
          <p class="muted-text image-file-help">点击识别后，图片会发送给已配置的 AI 服务。请先裁掉无关信息。</p>
          <div class="image-actions">
            <button
              class="button"
              type="button"
              :disabled="busy || !selectedFile || !canEdit || recognized || !online"
              @click="recognize"
            >
              <Sparkles aria-hidden="true" />{{ parsing ? '正在识别截图…' : recognized ? '已识别，请核对' : '识别这张截图' }}
            </button>
            <button v-if="parsing" class="button secondary" type="button" @click="cancelParsing">取消识别</button>
            <button v-if="selectedFile" class="button ghost" type="button" :disabled="busy" @click="clearImage">清空</button>
          </div>
        </section>

        <section ref="resultsRef" class="image-results" aria-label="识别结果" :aria-busy="parsing">
          <div class="image-result-heading">
            <h3>核对这几笔</h3>
            <span v-if="rows.length" class="muted-text" role="status">待核对 {{ pendingCount }} 笔 · 已保存 {{ savedCount }} 笔<span v-if="queuedCount"> · 待同步 {{ queuedCount }} 笔</span></span>
          </div>
          <div v-if="warnings.length" class="image-warnings" role="status">
            <TriangleAlert aria-hidden="true" />
            <ul><li v-for="(warning, index) in warnings" :key="index">{{ warning }}</li></ul>
          </div>
          <div v-if="!rows.length" class="empty-state image-empty" role="status">
            {{ parsing ? '正在提取金额、日期和商户，并匹配收支分类…' : '识别结果会显示在这里，确认之前不会记入账本。' }}
          </div>
          <template v-else>
            <div class="image-row-list" aria-label="选择待核对交易">
              <button
                v-for="row in rows"
                :key="row.key"
                class="image-row-button"
                :class="{ active: row.key === activeKey, done: row.status !== 'pending' }"
                type="button"
                :disabled="saving || row.status !== 'pending'"
                :aria-pressed="row.key === activeKey"
                @click="selectRow(row.key)"
              >
                <span class="image-row-description">{{ row.draft.description || '待补充描述' }}</span>
                <span class="image-row-amount num">{{ row.draft.type === 'INCOME' ? '+' : '−' }}¥{{ formatPlainMoney(row.draft.amount) }}</span>
                <span v-if="row.status !== 'pending'" class="image-row-status">{{ row.status === 'saved' ? '已保存' : row.status === 'queued' ? '待同步' : '已跳过' }}</span>
              </button>
            </div>
            <p v-if="categoryError" class="message error">
              分类加载失败，暂时不能保存。
              <button class="button ghost" type="button" @click="loadCategories">重新加载分类</button>
            </p>
            <form v-if="activeRow" class="form-grid image-result-form" @submit.prevent="saveActive">
              <fieldset class="image-fields" :disabled="saving || !canEdit">
                <div class="form-field">
                  <label for="image-amount">金额</label>
                  <input id="image-amount" inputmode="decimal" v-model="activeRow.draft.amount" class="input num" type="number" min="0.01" step="0.01" required />
                </div>
                <div class="form-field">
                  <label for="image-type">类型</label>
                  <select id="image-type" v-model="activeRow.draft.type" class="select" @change="activeRow.draft.categoryId = null">
                    <option value="EXPENSE">支出</option>
                    <option value="INCOME">收入</option>
                  </select>
                </div>
                <div class="form-field">
                  <label for="image-date">日期</label>
                  <input id="image-date" v-model="activeRow.draft.transactionDate" class="input" type="date" required />
                  <span v-if="!activeRow.draft.transactionDate" class="image-required-hint">截图中未能确认，请补充日期</span>
                </div>
                <div class="form-field">
                  <label for="image-account">账户</label>
                  <select id="image-account" v-model="activeRow.draft.accountId" class="select">
                    <option :value="null">未指定账户</option>
                    <option v-for="account in activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
                  </select>
                </div>
                <div class="form-field full">
                  <label for="image-category">分类</label>
                  <CategorySelector
                    input-id="image-category"
                    :model-value="activeRow.draft.categoryId"
                    :categories="currentCategories"
                    :allow-empty="false"
                    :disabled="saving || !canEdit"
                    placeholder="请选择分类"
                    @update:model-value="activeRow.draft.categoryId = $event ? Number($event) : null"
                  />
                </div>
                <div class="form-field full">
                  <label for="image-description">描述</label>
                  <input id="image-description" v-model.trim="activeRow.draft.description" class="input" required />
                </div>
                <div class="form-field">
                  <label for="image-merchant">商户 / 交易对方</label>
                  <input id="image-merchant" v-model.trim="activeRow.draft.parsedMerchant" class="input" placeholder="可选" />
                </div>
                <div class="form-field">
                  <label for="image-related-user">相关人员</label>
                  <input id="image-related-user" v-model.trim="activeRow.draft.relatedUser" class="input" placeholder="可选" />
                </div>
                <div class="form-field full">
                  <label for="image-note">备注</label>
                  <textarea id="image-note" v-model.trim="activeRow.draft.note" class="textarea image-note" placeholder="可选" />
                </div>
              </fieldset>
              <div class="form-field full image-save-actions">
                <button class="button" type="submit" :disabled="saving || !canEdit || categoryError">
                  {{ saving ? '保存中…' : online ? '确认并记下这一笔' : '保存，联网后同步' }}
                </button>
                <button class="button ghost" type="button" :disabled="saving || !canEdit" @click="skipActive">跳过这一笔</button>
              </div>
            </form>
            <div v-else class="image-complete" role="status">
              <CheckCircle2 aria-hidden="true" />
              <p>这张截图已核对完成，已保存 {{ savedCount }} 笔<span v-if="queuedCount">，待同步 {{ queuedCount }} 笔</span>。</p>
              <button class="button secondary" type="button" @click="requestClose(false)">完成</button>
            </div>
          </template>
        </section>
      </div>
    </div>
  </component>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { CheckCircle2, ImagePlus, Sparkles, TriangleAlert, Upload } from 'lucide-vue-next'
import { categoriesApi, transactionsApi } from '@/api'
import { ApiError } from '@/api/http'
import CategorySelector from '@/components/CategorySelector.vue'
import UiModal from '@/components/UiModal.vue'
import { useDraft } from '@/composables/useDraft'
import { useConnectivity } from '@/composables/useConnectivity'
import { saveOrQueueTransaction } from '@/stores/outbox'
import { identifySubmission, type SubmissionIdentity } from '@/utils/submission'
import { useLedgerStore } from '@/stores/ledger'
import type { AccountOption, Category, ImageTransactionDraft, SaveTransactionRequest, TransactionCapabilities } from '@/types'
import { flattenCategories } from '@/utils/category'
import { formatPlainMoney } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  capabilities: TransactionCapabilities
  accounts: AccountOption[]
  embedded?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [result?: { queued: boolean }]
  busy: [value: boolean]
}>()

interface DraftRow {
  key: number
  draft: ImageTransactionDraft
  status: 'pending' | 'saved' | 'skipped' | 'queued'
  submission?: SubmissionIdentity
}

const ledger = useLedgerStore()
const scopedLedgerId = ledger.state.activeLedger?.id
const sameLedger = computed(() => ledger.state.activeLedger?.id === scopedLedgerId)
const { online } = useConnectivity()
const { draft: savedDraft, clear: clearDraft } = useDraft('image-transactions', { rows: [] as DraftRow[], warnings: [] as string[] })
const fileInput = ref<HTMLInputElement | null>(null)
const resultsRef = ref<HTMLElement | null>(null)
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const parsing = ref(false)
const saving = ref(false)
const rows = computed({ get: () => sameLedger.value ? savedDraft.value.rows : [], set: value => { savedDraft.value.rows = value } })
const warnings = computed({ get: () => savedDraft.value.warnings, set: value => { savedDraft.value.warnings = value } })
const recognized = computed(() => rows.value.length > 0)
const activeKey = ref<number | null>(rows.value.find(row => row.status === 'pending')?.key ?? null)
const categories = ref<Category[]>([])
const categoryError = ref(false)
const errorText = ref('')
let generation = 0
let parseController: AbortController | null = null

const canEdit = computed(() => sameLedger.value && ledger.canEdit.value && props.capabilities.imageParsingEnabled)
const busy = computed(() => parsing.value || saving.value)
const activeAccounts = computed(() => props.accounts.filter(account => account.active))
const sizeLimitMb = computed(() => Math.round(props.capabilities.maxImageSizeBytes / 1024 / 1024))
const activeRow = computed(() => rows.value.find(row => row.key === activeKey.value && row.status === 'pending'))
const currentCategories = computed(() => flattenCategories(categories.value).filter(category => category.type === activeRow.value?.draft.type))
const pendingCount = computed(() => rows.value.filter(row => row.status === 'pending').length)
const savedCount = computed(() => rows.value.filter(row => row.status === 'saved').length)
const queuedCount = computed(() => rows.value.filter(row => row.status === 'queued').length)
watch(saving, value => emit('busy', value))

watch(() => props.modelValue, open => {
  if (open) void loadCategories()
  else if (!props.embedded) cancelParsing()
}, { immediate: true })

watch(() => ledger.state.activeLedger?.id, () => {
  releaseImage()
  categories.value = []
  emit('update:modelValue', false)
})

watch(canEdit, allowed => {
  if (!allowed) cancelParsing()
})

onBeforeUnmount(releaseImage)

async function loadCategories(): Promise<void> {
  const currentGeneration = generation
  try {
    const result = await categoriesApi.list()
    if (currentGeneration !== generation) return
    categories.value = result
    categoryError.value = false
  } catch {
    if (currentGeneration === generation) categoryError.value = true
  }
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  if (input.files?.[0]) chooseFile(input.files[0])
  input.value = ''
}

function onDrop(event: DragEvent): void {
  if (event.dataTransfer?.files.length !== 1) {
    errorText.value = '请一次选择一张截图。'
    return
  }
  chooseFile(event.dataTransfer.files[0])
}

function onPaste(event: ClipboardEvent): void {
  const files = event.clipboardData?.files
  if (!files?.length) return
  event.preventDefault()
  if (files.length !== 1) {
    errorText.value = '请一次粘贴一张截图。'
    return
  }
  chooseFile(files[0])
}

function chooseFile(file: File): void {
  if (busy.value || !canEdit.value) return
  if (!props.capabilities.supportedImageTypes.includes(file.type)) {
    errorText.value = '请选择 PNG 或 JPG 格式的图片。'
    return
  }
  if (!file.size || file.size > props.capabilities.maxImageSizeBytes) {
    errorText.value = `请选择非空且不超过 ${sizeLimitMb.value} MB 的图片。`
    return
  }
  resetImage()
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  void loadCategories()
}

function clearImage(): void {
  if (!busy.value) resetImage()
}

function releaseImage(): void {
  generation++
  cancelParsing()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  selectedFile.value = null
}

function resetImage(): void {
  releaseImage()
  clearDraft()
  rows.value = []
  activeKey.value = null
  warnings.value = []
  errorText.value = ''
  saving.value = false
}

function cancelParsing(): void {
  parseController?.abort()
  parseController = null
  parsing.value = false
}

async function recognize(): Promise<void> {
  const ledgerId = ledger.state.activeLedger?.id
  if (!online.value || !selectedFile.value || !ledgerId || !canEdit.value || busy.value || recognized.value) return
  const currentGeneration = generation
  const controller = new AbortController()
  parseController = controller
  parsing.value = true
  errorText.value = ''
  try {
    const result = await transactionsApi.parseImage(selectedFile.value, ledgerId, controller.signal)
    if (currentGeneration !== generation || parseController !== controller || (!props.embedded && !props.modelValue) || !canEdit.value) return
    rows.value = result.transactions.map((draft, key) => ({
      key,
      draft: { ...draft, accountId: draft.accountId ?? null },
      status: 'pending',
      submission: { fingerprint: '', key: '' }
    }))
    warnings.value = result.warnings || []
    activeKey.value = rows.value[0]?.key ?? null
    if (!rows.value.length) errorText.value = '没有识别到可入账的收支，请换一张清晰的消费或收入截图。'
    if (!categories.value.length) void loadCategories()
    if (rows.value.length) scrollToResults()
  } catch (error) {
    if (currentGeneration === generation && parseController === controller) {
      errorText.value = error instanceof ApiError ? error.message : '截图识别失败，请稍后重试或换一张图片。'
    }
  } finally {
    if (currentGeneration === generation && parseController === controller) {
      parsing.value = false
      parseController = null
    }
  }
}

function selectRow(key: number): void {
  if (saving.value) return
  activeKey.value = key
  errorText.value = ''
}

function advance(): void {
  activeKey.value = rows.value.find(row => row.status === 'pending')?.key ?? null
  scrollToResults()
}

function scrollToResults(): void {
  if (!window.matchMedia('(max-width: 820px)').matches) return
  void nextTick(() => {
    resultsRef.value?.scrollIntoView({
      block: 'start',
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
    })
  })
}

function skipActive(): void {
  if (!activeRow.value || saving.value || !canEdit.value) return
  activeRow.value.status = 'skipped'
  errorText.value = ''
  advance()
}

async function saveActive(): Promise<void> {
  const row = activeRow.value
  const ledgerId = ledger.state.activeLedger?.id
  if (!row || !ledgerId || saving.value || !canEdit.value) return
  const draft = row.draft
  const amount = Number(draft.amount)
  if (!Number.isFinite(amount) || amount <= 0 || !draft.description.trim() || !draft.transactionDate) {
    errorText.value = '请补全有效金额、日期和描述后再保存。'
    return
  }
  if (categoryError.value || !currentCategories.value.some(category => category.id === draft.categoryId)) {
    errorText.value = '请选择与收支类型一致的分类后再保存。'
    return
  }
  if (draft.accountId != null && !activeAccounts.value.some(account => account.id === draft.accountId)) {
    errorText.value = '该账户不可用，请重新选择账户。'
    return
  }
  const payload: SaveTransactionRequest = {
    ...draft,
    categoryId: draft.categoryId!,
    transactionDate: draft.transactionDate,
    originalText: draft.originalText || draft.description,
    tagIds: draft.tags?.map(tag => tag.id) || []
  }
  const currentGeneration = generation
  saving.value = true
  errorText.value = ''
  try {
    row.submission ||= { fingerprint: '', key: '' }
    const result = await saveOrQueueTransaction(identifySubmission(payload, row.submission), ledgerId)
    if (currentGeneration !== generation || ledger.state.activeLedger?.id !== ledgerId) return
    row.status = result.queued ? 'queued' : 'saved'
    advance()
    emit('saved', result)
  } catch (error) {
    if (currentGeneration === generation) {
      errorText.value = error instanceof Error ? error.message : '保存失败，请重试。'
    }
  } finally {
    if (currentGeneration === generation) saving.value = false
  }
}

function requestClose(value: boolean): void {
  if (saving.value) return
  emit('update:modelValue', value)
}
</script>

<style scoped>
.image-workspace { display: grid; grid-template-columns: minmax(220px, 0.7fr) minmax(0, 1.3fr); gap: var(--space-lg); align-items: start; }
.image-source, .image-results { min-width: 0; }
.image-results { scroll-margin-top: 120px; }
.image-file-input { display: none; }
.image-dropzone { display: grid; justify-items: center; gap: 14px; min-height: 280px; padding: 28px 16px; border: 1px dashed var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-soft); text-align: center; }
.image-dropzone p { margin: 0; color: var(--color-muted); line-height: 1.7; font-size: 14px; }
.image-placeholder-icon { width: 36px; height: 36px; color: var(--color-primary); }
.image-dropzone.has-preview { padding: 12px; gap: 12px; }
.image-preview { width: 100%; max-height: 460px; object-fit: contain; border-radius: 6px; }
.image-file-name { overflow-wrap: anywhere; margin: 10px 0 4px; font-size: 13px; }
.image-file-help { line-height: 1.6; font-size: 12px; margin: 8px 0; }
.image-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.image-result-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 6px; margin-bottom: 16px; }
.image-result-heading h3 { margin: 0; font-size: 18px; }
.image-result-heading > span { font-size: 12px; }
.image-empty { min-height: 280px; padding: 36px 20px; line-height: 1.8; }
.image-warnings { display: flex; align-items: flex-start; gap: 10px; border-left: 3px solid var(--color-warning); background: var(--color-surface-soft); padding: 12px; margin-bottom: 16px; font-size: 13px; line-height: 1.6; }
.image-warnings > svg { flex-shrink: 0; width: 18px; height: 18px; color: var(--color-warning); }
.image-warnings ul { padding-left: 16px; margin: 0; }
.image-row-list { display: grid; gap: 6px; max-height: 210px; overflow-y: auto; padding: 3px; margin: -3px -3px 20px; }
.image-row-button { display: flex; align-items: center; gap: 8px; padding: 12px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); text-align: left; }
.image-row-button.active { border-color: var(--color-primary); background: var(--color-primary-soft); }
.image-row-button:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 1px; }
.image-row-button.done { background: var(--color-surface-soft); color: var(--color-muted); }
.image-row-description { flex: 1; min-width: 0; overflow-wrap: anywhere; font-size: 14px; }
.image-row-amount { flex-shrink: 0; font-size: 17px; }
.image-row-status { flex-shrink: 0; color: var(--color-success-text); font-size: 12px; }
.image-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); grid-column: 1 / -1; margin: 0; padding: 0; min-width: 0; border: 0; }
.image-required-hint { font-size: 12px; color: var(--color-danger-text); }
.image-note { min-height: 68px; }
.image-save-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.image-complete { display: grid; justify-items: center; gap: 12px; padding: 28px 12px; text-align: center; }
.image-complete > svg { width: 32px; height: 32px; color: var(--color-success); }
.image-complete p { margin: 0; }
@media (max-width: 820px) {
  .image-workspace { grid-template-columns: minmax(0, 1fr); }
  .image-dropzone { min-height: 200px; }
  .image-preview { max-height: 260px; }
  .image-empty { min-height: 130px; }
  .image-actions > .button:first-child, .image-save-actions > .button:first-child { flex: 1; }
}
@media (max-width: 380px) {
  .image-fields { grid-template-columns: minmax(0, 1fr); }
}
</style>
