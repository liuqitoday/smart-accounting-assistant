<template>
  <AppShell>
    <PageHeader title="账单明细" subtitle="按日期、类型和标签筛选,支持编辑、导入与导出。">
      <template #actions>
        <button class="button secondary recurring-bills-btn" type="button" @click="recurringOpen = true">
          <CalendarClock />
          周期账单
        </button>
        <button class="button secondary import-export-btn" type="button" :disabled="!ledger.canEdit.value" @click="openImportModal">
          <Upload />
          导入 CSV
        </button>
        <button class="button import-export-btn" type="button" :disabled="exporting" @click="exportCsv">
          <Download />
          {{ exporting ? '导出中...' : '导出' }}
        </button>
      </template>
    </PageHeader>

    <!-- 撤销窗口期由 remove() 的 5 秒定时器负责收尾,期间禁用横幅自动淡出 -->
    <MessageBanner :message="message.text" :type="message.type" :auto-dismiss="!deletedTransactionId" @dismissed="clear">
      <template v-if="deletedTransactionId" #actions>
        <button class="banner-action-btn" type="button" @click="undoDelete">撤销</button>
      </template>
    </MessageBanner>

    <section class="section-stack">
      <div class="filter-accordion t-acc" :data-open="String(filtersOpen)">
        <button
          class="button secondary compact filter-toggle mobile-only t-acc-head"
          type="button"
          aria-controls="transaction-filters"
          :aria-expanded="filtersOpen"
          @click="filtersOpen = !filtersOpen"
        >
          <SlidersHorizontal />
          {{ filtersOpen ? '收起筛选' : '筛选' }}
          <span class="t-acc-chevron" aria-hidden="true">
            <ChevronDown />
          </span>
        </button>

        <div
          id="transaction-filters"
          class="t-acc-panel"
          :aria-hidden="!filtersAccessible"
          :inert="filtersAccessible ? undefined : true"
        >
          <div class="t-acc-panel-inner">
            <div class="filter-bar">
              <div class="form-field full">
                <label for="transaction-search">关键词搜索</label>
                <div class="search-input-wrapper">
                  <input
                    id="transaction-search"
                    v-model="searchKeyword"
                    class="input search-input-field"
                    type="text"
                    placeholder="搜索描述、商户、备注..."
                    @keydown.enter="applyFilters"
                  />
                  <svg v-if="!searchKeyword" class="search-icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="11" cy="11" r="8"></circle>
                    <path d="m21 21-4.35-4.35"></path>
                  </svg>
                  <button v-if="searchKeyword" class="clear-search-btn" type="button" aria-label="清空搜索" title="清空搜索" @click="searchKeyword = ''">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <line x1="18" y1="6" x2="6" y2="18"></line>
                      <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                  </button>
                </div>
              </div>
              <div class="form-field">
                <label for="transaction-filter-type">类型</label>
                <div class="filter-select-control" :class="{ 'has-value': filters.type }">
                  <select id="transaction-filter-type" v-model="filters.type" class="select">
                    <option value="">全部</option>
                    <option value="EXPENSE">支出</option>
                    <option value="INCOME">收入</option>
                    <option value="TRANSFER">转账</option>
                  </select>
                  <button
                    v-if="filters.type"
                    class="filter-clear-btn"
                    type="button"
                    aria-label="清除类型"
                    title="清除类型"
                    @click="filters.type = ''"
                  >
                    <X />
                  </button>
                </div>
              </div>
              <div class="form-field">
                <label for="transaction-start-date">开始日期</label>
                <input id="transaction-start-date" v-model="filters.startDate" class="input" type="date" />
              </div>
              <div class="form-field">
                <label for="transaction-end-date">结束日期</label>
                <input id="transaction-end-date" v-model="filters.endDate" class="input" type="date" />
              </div>
              <div class="form-field">
                <label for="transaction-tag-filter">标签</label>
                <div class="filter-select-control" :class="{ 'has-value': tagFilterProxy }">
                  <select id="transaction-tag-filter" v-model="tagFilterProxy" class="select">
                    <option value="">全部标签</option>
                    <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
                  </select>
                  <button
                    v-if="tagFilterProxy"
                    class="filter-clear-btn"
                    type="button"
                    aria-label="清除标签"
                    title="清除标签"
                    @click="tagFilterProxy = ''"
                  >
                    <X />
                  </button>
                </div>
              </div>
              <div class="form-field">
                <label for="transaction-category-filter">分类</label>
                <CategorySelector v-model="categoryFilterProxy" input-id="transaction-category-filter" :categories="allCategories" />
              </div>
              <div class="form-field">
                <label for="transaction-created-by">创建人</label>
                <input id="transaction-created-by" v-model.trim="filters.createdBy" class="input" type="text" placeholder="输入用户名" />
              </div>
              <div class="form-field">
                <label for="transaction-account-filter">账户</label>
                <div class="filter-select-control" :class="{ 'has-value': filters.accountId }">
                  <select id="transaction-account-filter" v-model="accountFilterProxy" class="select">
                    <option value="">全部账户</option>
                    <option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.name }}</option>
                  </select>
                  <button
                    v-if="filters.accountId"
                    class="filter-clear-btn"
                    type="button"
                    aria-label="清除账户"
                    title="清除账户"
                    @click="accountFilterProxy = ''"
                  >
                    <X />
                  </button>
                </div>
              </div>
              <div class="form-field">
                <label for="transaction-merchant-filter">商家</label>
                <input id="transaction-merchant-filter" v-model.trim="merchantFilter" class="input" type="text" placeholder="商家名称" />
              </div>
              <div class="form-field">
                <label for="transaction-min-amount">最小金额</label>
                <input id="transaction-min-amount" v-model="minAmountFilter" class="input" type="number" step="0.01" min="0" />
              </div>
              <div class="form-field">
                <label for="transaction-max-amount">最大金额</label>
                <input id="transaction-max-amount" v-model="maxAmountFilter" class="input" type="number" step="0.01" min="0" />
              </div>
              <div class="filter-actions">
                <button class="button" type="button" @click="applyFilters">查询</button>
                <button class="button secondary" type="button" @click="resetFilters">重置</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="flow-toolbar">
        <h2 class="panel-title">交易明细</h2>
        <button class="button ghost compact" type="button" @click="toggleSort">
          日期 {{ sortDirection === 'desc' ? '↓' : '↑' }}
        </button>
      </div>

      <div v-if="loading" class="loading-state">
        <span class="t-shimmer" data-text="正在翻账本...">正在翻账本...</span>
      </div>
      <div v-else-if="!page.content.length" class="empty-state coins">这段时间还没有账单,去记一笔吧</div>
      <TransactionFlow
        v-else
        :transactions="page.content"
        show-actions
        :can-edit="ledger.canEdit.value"
        @edit="openEdit"
        @remove="remove"
      />

      <div class="pagination">
        <button class="button secondary compact" type="button" :disabled="page.first" @click="loadTransactions(page.number - 1)">上一页</button>
        <span class="muted-text">第 {{ page.number + 1 }} / {{ Math.max(page.totalPages, 1) }} 页，共 {{ page.totalElements }} 条</span>
        <button class="button secondary compact" type="button" :disabled="page.last || page.totalPages === 0" @click="loadTransactions(page.number + 1)">下一页</button>
      </div>
    </section>

    <UiModal v-model="editOpen" title="编辑交易" size="lg">
      <form v-if="editForm" class="form-grid" @submit.prevent="saveEdit">
        <div class="form-field">
          <label for="edit-transaction-amount">金额</label>
          <input id="edit-transaction-amount" v-model="editForm.amount" class="input" type="number" step="0.01" required />
        </div>
        <div class="form-field">
          <label for="edit-transaction-type">类型</label>
          <select id="edit-transaction-type" v-model="editForm.type" class="select" @change="onEditTypeChange">
            <option value="EXPENSE">支出</option>
            <option value="INCOME">收入</option>
          </select>
        </div>
        <div class="form-field">
          <label for="edit-transaction-date">日期</label>
          <input id="edit-transaction-date" v-model="editForm.transactionDate" class="input" type="date" required />
        </div>
        <div class="form-field">
          <label for="edit-transaction-category">分类</label>
          <CategorySelector
            input-id="edit-transaction-category"
            v-model="editForm.categoryId"
            :categories="flatEditCategories"
            :allow-empty="false"
            placeholder="请选择分类"
            search-placeholder="搜索分类名称或父级..."
          />
        </div>
        <div class="form-field">
          <label for="edit-transaction-account">账户</label>
          <select id="edit-transaction-account" v-model="editAccountProxy" class="select">
            <option value="">未指定账户</option>
            <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
              {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
            </option>
          </select>
        </div>
        <div class="form-field">
          <label for="edit-transaction-merchant">商户</label>
          <input id="edit-transaction-merchant" v-model.trim="editForm.parsedMerchant" class="input" />
        </div>
        <div class="form-field">
          <label for="edit-transaction-related-user">相关人员</label>
          <input id="edit-transaction-related-user" v-model.trim="editForm.relatedUser" class="input" />
        </div>
        <div class="form-field full">
          <label for="edit-transaction-description">描述</label>
          <input id="edit-transaction-description" v-model.trim="editForm.description" class="input" required />
        </div>
        <div class="form-field full">
          <label for="edit-transaction-note">备注</label>
          <textarea id="edit-transaction-note" v-model.trim="editForm.note" class="textarea" />
        </div>
        <div class="form-field full">
          <span id="edit-transaction-tags-label" class="field-label">标签</span>
          <TagSelector labelledby="edit-transaction-tags-label" :tags="tags" :selected-ids="editTagIds" @update:selected-ids="editTagIds = $event" />
        </div>
        <div class="form-field full">
          <MessageBanner :message="editMessage.text" :type="editMessage.type" @dismissed="clearEditMessage" />
        </div>
        <div class="form-field full row-actions">
          <button class="button" type="submit" :disabled="savingEdit">保存</button>
          <button class="button secondary" type="button" @click="editOpen = false">取消</button>
        </div>
      </form>
    </UiModal>

    <UiModal v-model="importOpen" title="导入 CSV" subtitle="选择随手记兼容 CSV 文件，导入到当前账本。" size="md">
      <div class="grid">
        <div class="form-field">
          <label for="transaction-import-file">CSV 文件</label>
          <input id="transaction-import-file" class="input" type="file" accept=".csv,text/csv" @change="onFileChange" />
        </div>
        <button class="button" type="button" :disabled="!importFile || importing" @click="submitImport">
          {{ importing ? '导入中...' : '开始导入' }}
        </button>
        <MessageBanner :message="importMessage.text" :type="importMessage.type" @dismissed="clearImportMessage" />
        <div v-if="importResult" class="import-summary">
          <div class="grid cols-4">
            <div class="stat-card"><span class="stat-label">总行数</span><strong>{{ importResult.totalRows }}</strong></div>
            <div class="stat-card"><span class="stat-label">新增</span><strong>{{ importResult.createdCount }}</strong></div>
            <div class="stat-card"><span class="stat-label">更新</span><strong>{{ importResult.updatedCount }}</strong></div>
            <div class="stat-card"><span class="stat-label">错误</span><strong>{{ importResult.errorCount }}</strong></div>
          </div>
          <div v-if="importResult.errors?.length" class="panel">
            <div v-for="error in importResult.errors" :key="`${error.row}-${error.field}-${error.message}`" class="import-error">
              第 {{ error.row }} 行：{{ error.field }} {{ error.message }}
            </div>
          </div>
        </div>
      </div>
    </UiModal>

    <RecurringBillsModal v-model="recurringOpen" :can-edit="ledger.canEdit.value" @generated="onRecurringGenerated" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CalendarClock, ChevronDown, Download, SlidersHorizontal, Upload, X } from 'lucide-vue-next'
import { accountsApi, categoriesApi, tagsApi, transactionsApi } from '@/api'
import { flattenCategories } from '@/utils/category'
import AppShell from '@/components/AppShell.vue'
import CategorySelector from '@/components/CategorySelector.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import PageHeader from '@/components/PageHeader.vue'
import RecurringBillsModal from '@/components/RecurringBillsModal.vue'
import TagSelector from '@/components/TagSelector.vue'
import TransactionFlow from '@/components/TransactionFlow.vue'
import UiModal from '@/components/UiModal.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useMessage } from '@/composables/useMessage'
import { useLedgerStore } from '@/stores/ledger'
import type { Account, Category, ImportResult, PageResponse, Tag, Transaction, TransactionFilters, UpdateTransactionRequest } from '@/types'
import { defaultAccountIcon, downloadBlob } from '@/utils/format'
import { hydrateFiltersFromQuery, normalizedQueryKey } from '@/utils/transactionQuery'

const ledger = useLedgerStore()
const route = useRoute()
const loading = ref(false)
const savingEdit = ref(false)
const editOpen = ref(false)
const importOpen = ref(false)
const recurringOpen = ref(false)
const importing = ref(false)
const exporting = ref(false)
const sortDirection = ref<'asc' | 'desc'>('desc')
const filtersOpen = ref(false)
const isMobileFilters = ref(false)
const importFile = ref<File | null>(null)
const importResult = ref<ImportResult | null>(null)
const tags = ref<Tag[]>([])
const accounts = ref<Account[]>([])
const categories = ref<Category[]>([])
const editCategories = ref<Category[]>([])
const editForm = ref<Partial<Transaction> | null>(null)
const editTagIds = ref<number[]>([])
const filters = reactive<TransactionFilters>({
  type: '',
  startDate: '',
  endDate: '',
  tagIds: [],
  categoryId: undefined,
  size: 20
})
const searchKeyword = ref('')
const lastHydratedKey = ref('')
const deletedTransactionId = ref<number | null>(null)
const deleteTimer = ref<number | null>(null)
const page = reactive<PageResponse<Transaction>>({
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
  first: true,
  last: true
})
const { message, show, showSuccess, showInfo, showError, clear } = useMessage()
const { message: editMessage, show: showEdit, showError: showEditError, clear: clearEditMessage } = useMessage()
const { message: importMessage, showSuccess: showImportSuccess, showError: showImportError, clear: clearImportMessage } = useMessage()
const { confirm } = useConfirm()

const UNDO_HINT = '已删除。5秒内可撤销。'

const tagFilterProxy = computed({
  get: () => filters.tagIds?.[0] ?? '',
  set: value => {
    filters.tagIds = value === '' ? [] : [Number(value)]
  }
})
const allCategories = computed(() => flattenCategories(categories.value))
const categoryFilterProxy = computed({
  get: () => filters.categoryId ?? '',
  set: value => {
    filters.categoryId = value === '' ? undefined : Number(value)
  }
})
const accountFilterProxy = computed({
  get: () => filters.accountId ?? '',
  set: value => {
    filters.accountId = value === '' ? undefined : Number(value)
  }
})
const merchantFilter = computed({
  get: () => filters.merchant ?? '',
  set: value => {
    filters.merchant = value === '' ? undefined : value
  }
})
const minAmountFilter = computed({
  get: () => filters.minAmount ?? '',
  set: value => {
    filters.minAmount = value === '' ? undefined : value
  }
})
const maxAmountFilter = computed({
  get: () => filters.maxAmount ?? '',
  set: value => {
    filters.maxAmount = value === '' ? undefined : value
  }
})
const activeAccounts = computed(() => accounts.value.filter(account => account.active))
const filtersAccessible = computed(() => !isMobileFilters.value || filtersOpen.value)
const flatEditCategories = computed(() => flattenCategories(editCategories.value))
const editAccountProxy = computed({
  get: () => editForm.value?.accountId ?? '',
  set: value => {
    if (editForm.value) {
      editForm.value.accountId = value === '' ? null : Number(value)
    }
  }
})
let mobileFilterMedia: MediaQueryList | undefined

onMounted(async () => {
  mobileFilterMedia = window.matchMedia('(max-width: 820px)')
  syncMobileFilterState()
  mobileFilterMedia.addEventListener('change', syncMobileFilterState)
  await Promise.all([loadTags(), loadAccounts(), loadCategories()]).catch((error: unknown) => {
    showError(error, '筛选数据加载失败，请刷新重试')
  })
})

watch(() => route.query, hydrateAndLoad, { immediate: true })

onBeforeUnmount(() => {
  mobileFilterMedia?.removeEventListener('change', syncMobileFilterState)
})

function syncMobileFilterState(): void {
  isMobileFilters.value = mobileFilterMedia?.matches ?? false
}

/** 列表查询与 CSV 导出共用的当前筛选条件（含关键词） */
function currentFilterParams(): TransactionFilters {
  return {
    ...filters,
    keyword: searchKeyword.value.trim() || undefined
  }
}

function applyHydratedFilters(next: TransactionFilters): void {
  filters.type = next.type || ''
  filters.startDate = next.startDate || ''
  filters.endDate = next.endDate || ''
  filters.tagIds = next.tagIds ?? []
  filters.categoryId = next.categoryId
  filters.accountId = next.accountId
  filters.merchant = next.merchant
  filters.minAmount = next.minAmount
  filters.maxAmount = next.maxAmount
  filters.createdBy = next.createdBy || ''
  searchKeyword.value = next.keyword || ''
}

function hydrateAndLoad(): void {
  const key = normalizedQueryKey(route.query)
  if (key === lastHydratedKey.value) return
  lastHydratedKey.value = key
  applyHydratedFilters(hydrateFiltersFromQuery(route.query))
  void loadTransactions(0)
}

async function loadTransactions(pageNumber = page.number): Promise<void> {
  loading.value = true
  try {
    // 统一使用 list API，支持搜索+筛选组合（AND逻辑）
    const result = await transactionsApi.list({
      ...currentFilterParams(),
      page: pageNumber,
      sort: `transactionDate,${sortDirection.value}`
    })
    Object.assign(page, result)
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

function clearDeleteTimer(): void {
  if (deleteTimer.value) {
    clearTimeout(deleteTimer.value)
    deleteTimer.value = null
  }
}

async function loadTags(): Promise<void> {
  tags.value = await tagsApi.list()
}

async function loadAccounts(): Promise<void> {
  accounts.value = await accountsApi.list()
}

async function loadCategories(): Promise<void> {
  categories.value = await categoriesApi.list()
}

function applyFilters(): void {
  filtersOpen.value = false
  void loadTransactions(0)
}

function resetFilters(): void {
  filters.type = ''
  filters.startDate = ''
  filters.endDate = ''
  filters.tagIds = []
  filters.categoryId = undefined
  filters.accountId = undefined
  filters.merchant = undefined
  filters.minAmount = undefined
  filters.maxAmount = undefined
  filters.createdBy = ''
  searchKeyword.value = ''
  lastHydratedKey.value = ''
  void loadTransactions(0)
}

function toggleSort(): void {
  sortDirection.value = sortDirection.value === 'desc' ? 'asc' : 'desc'
  void loadTransactions(0)
}

async function openEdit(transaction: Transaction): Promise<void> {
  if (transaction.type === 'TRANSFER') {
    show('error', '转账不能通过普通交易表单编辑。')
    return
  }
  try {
    const detail = await transactionsApi.get(transaction.id)
    if (detail.type === 'TRANSFER') {
      show('error', '转账不能通过普通交易表单编辑。')
      return
    }
    editForm.value = { ...detail }
    editTagIds.value = detail.tags?.map(tag => tag.id) || []
    await loadEditCategories()
    clearEditMessage()
    editOpen.value = true
  } catch (error) {
    showError(error, '交易详情加载失败，请稍后重试')
  }
}

async function loadEditCategories(): Promise<void> {
  if (!editForm.value?.type) return
  editCategories.value = await categoriesApi.list(editForm.value.type)
}

/** 编辑弹窗内切换类型时重载分类（弹窗已打开，报错落弹窗内横幅） */
async function onEditTypeChange(): Promise<void> {
  try {
    await loadEditCategories()
  } catch (error) {
    showEditError(error, '分类加载失败，请稍后重试')
  }
}

async function saveEdit(): Promise<void> {
  if (!editForm.value?.id) return
  if (!editForm.value.categoryId) {
    showEdit('error', '请选择分类后再保存。')
    return
  }
  if (editForm.value.type === 'TRANSFER') {
    showEdit('error', '转账不能通过普通交易表单编辑。')
    return
  }
  savingEdit.value = true
  clearEditMessage()
  try {
    const payload: UpdateTransactionRequest = {
      transactionDate: editForm.value.transactionDate!,
      type: editForm.value.type!,
      amount: editForm.value.amount!,
      description: editForm.value.description!,
      parsedMerchant: editForm.value.parsedMerchant,
      note: editForm.value.note,
      relatedUser: editForm.value.relatedUser,
      categoryId: editForm.value.categoryId!,
      accountId: editForm.value.accountId
    }
    await transactionsApi.update(editForm.value.id, payload)
    await transactionsApi.updateTags(editForm.value.id, editTagIds.value)
    editOpen.value = false
    showSuccess('交易记录已更新。')
    await loadTransactions()
  } catch (error) {
    showEditError(error)
  } finally {
    savingEdit.value = false
  }
}

async function remove(transaction: Transaction): Promise<void> {
  if (!(await confirm({ title: '删除交易', message: `确认删除「${transaction.description}」？`, danger: true }))) return

  clearDeleteTimer()

  try {
    await transactionsApi.delete(transaction.id)
    deletedTransactionId.value = transaction.id
    showInfo(UNDO_HINT)
    await loadTransactions()

    // 5秒后清除撤销状态（横幅仍显示撤销提示时一并收起）
    deleteTimer.value = window.setTimeout(() => {
      deletedTransactionId.value = null
      deleteTimer.value = null
      if (message.text === UNDO_HINT) {
        clear()
      }
    }, 5000)
  } catch (error) {
    showError(error)
  }
}

async function undoDelete(): Promise<void> {
  if (!deletedTransactionId.value) return

  clearDeleteTimer()
  // 先退出撤销窗口期，让后续横幅恢复自动淡出
  const restoreId = deletedTransactionId.value
  deletedTransactionId.value = null

  try {
    await transactionsApi.restore(restoreId)
    showSuccess('已恢复。')
    await loadTransactions()
  } catch (error) {
    showError(error)
  }
}

async function exportCsv(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await transactionsApi.export(currentFilterParams())
    downloadBlob(blob, 'transactions.csv')
  } catch (error) {
    showError(error)
  } finally {
    exporting.value = false
  }
}

function onFileChange(event: Event): void {
  importFile.value = (event.target as HTMLInputElement).files?.[0] || null
  importResult.value = null
  clearImportMessage()
}

function openImportModal(): void {
  importFile.value = null
  importResult.value = null
  clearImportMessage()
  importOpen.value = true
}

async function submitImport(): Promise<void> {
  if (!importFile.value) return
  importing.value = true
  clearImportMessage()
  try {
    importResult.value = await transactionsApi.import(importFile.value)
    showImportSuccess('CSV 导入完成。')
    await loadTransactions(0)
  } catch (error) {
    showImportError(error)
  } finally {
    importing.value = false
  }
}

async function onRecurringGenerated(): Promise<void> {
  await Promise.all([
    loadAccounts().catch((error: unknown) => {
      showError(error, '账户数据刷新失败，请稍后重试')
    }),
    loadTransactions(0)
  ])
}
</script>

<style scoped>
/* 搜索输入框容器 */
.search-input-wrapper {
  position: relative;
  width: 100%;
}

.search-input-field {
  width: 100%;
  padding-left: 38px;
  padding-right: 38px;
}

.filter-select-control {
  position: relative;
}

.filter-select-control.has-value .select {
  padding-right: 68px;
}

.filter-clear-btn {
  position: absolute;
  top: 50%;
  right: 32px;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-muted);
  transform: translateY(-50%);
}

.filter-clear-btn:hover {
  background: var(--color-surface-soft);
  color: var(--color-text);
}

.filter-clear-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.filter-clear-btn svg {
  width: 15px;
  height: 15px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-muted);
  pointer-events: none;
}

.clear-search-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  padding: 6px;
  border: none;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease, color 0.15s ease;
}

.clear-search-btn:hover {
  background: var(--color-surface-soft);
  color: var(--color-text);
}

.banner-action-btn {
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: white;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.18s ease;
}

.banner-action-btn:hover {
  background: var(--color-primary-hover);
}

.flow-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: calc(var(--space-md) * -0.5);
}

.import-summary {
  display: grid;
  gap: var(--space-md);
}

.import-error {
  border-bottom: 1px solid var(--color-border);
  color: var(--color-danger-text);
  padding: 10px 12px;
}

.import-error:last-child {
  border-bottom: none;
}

/* 移动端隐藏导入导出按钮（低频操作，PC端使用）；周期账单保留入口。 */
@media (max-width: 820px) {
  .import-export-btn {
    display: none;
  }

  .recurring-bills-btn {
    width: 100%;
  }

  .filter-clear-btn {
    width: 40px;
    height: 40px;
  }
}
</style>
