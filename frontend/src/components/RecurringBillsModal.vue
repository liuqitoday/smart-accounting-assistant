<template>
  <UiModal
    :model-value="modelValue"
    title="周期账单"
    subtitle="管理固定重复发生的收入和支出。"
    size="xl"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="recurring-shell">
      <div class="recurring-command">
        <div class="recurring-metrics" aria-label="周期账单概览">
          <span><strong>{{ rules.length }}</strong> 条规则</span>
          <span><strong>{{ activeRuleCount }}</strong> 启用</span>
          <span><strong>{{ dueRuleCount }}</strong> 到期</span>
        </div>
        <div class="command-actions">
          <p
            v-if="message.text"
            class="recurring-status"
            :class="message.type"
            :role="message.type === 'error' ? 'alert' : 'status'"
            :aria-live="message.type === 'error' ? 'assertive' : 'polite'"
          >{{ message.text }}</p>
          <button
            class="button secondary compact"
            type="button"
            :disabled="loading || !canEdit || generatingAll || generatingRuleId !== null"
            @click="generateDue"
          >
            <RefreshCw />
            {{ generatingAll ? '处理中...' : '处理到期' }}
          </button>
        </div>
      </div>

      <div class="recurring-workbench">
        <section class="rules-pane" aria-label="周期账单规则">
          <div class="pane-heading">
            <div>
              <span class="pane-kicker">规则</span>
              <h3>自动记账排期</h3>
            </div>
            <button v-if="editingId" class="button ghost compact" type="button" @click="resetForm()">
              新建
            </button>
          </div>

          <div v-if="loading" class="loading-state pane-state">
            <span class="t-shimmer" data-text="正在读取规则...">正在读取规则...</span>
          </div>
          <div v-else-if="!rules.length" class="empty-state pane-state">还没有周期账单</div>
          <div v-else class="recurring-list">
            <article
              v-for="rule in rules"
              :key="rule.id"
              class="recurring-item"
              :class="{ selected: editingId === rule.id, inactive: !rule.enabled || isEnded(rule), ended: isEnded(rule) }"
            >
              <div class="rule-date">
                <span>每月</span>
                <strong>{{ rule.dayOfMonth }}</strong>
              </div>
              <div class="rule-main">
                <div class="rule-title-row">
                  <strong>{{ rule.name }}</strong>
                  <span class="rule-badge" :class="ruleStatusClass(rule)">
                    {{ ruleStatusLabel(rule) }}
                  </span>
                </div>
                <div class="rule-meta">
                  <span v-if="rule.type !== 'TRANSFER'">{{ categoryLabel(rule.categoryId) }}</span>
                  <span v-if="rule.type === 'TRANSFER'">{{ accountLabel(rule.accountId) }} → {{ accountLabel(rule.counterAccountId) }}</span>
                  <span v-else>{{ accountLabel(rule.accountId) }}</span>
                  <span v-if="rule.relatedUser">相关人员 {{ rule.relatedUser }}</span>
                </div>
                <div class="rule-schedule">
                  {{ isEnded(rule) ? `结束于 ${formatDate(rule.endDate)}` : `下一次 ${formatDate(rule.nextRunDate)}` }}
                </div>
              </div>
              <div class="rule-side">
                <!-- 金额统一显示 ¥xx（formatMoney 自带货币符号），收入/支出用颜色区分，与全站惯例一致 -->
                <span class="rule-amount" :class="rule.type.toLowerCase()">
                  {{ formatMoney(rule.amount) }}
                </span>
                <div class="rule-actions">
                  <button
                    class="icon-button"
                    type="button"
                    :disabled="!canEdit || isEnded(rule) || generatingAll || generatingRuleId !== null"
                    :title="isEnded(rule) ? '已结束' : '处理到期'"
                    :aria-label="isEnded(rule) ? '已结束' : '处理到期'"
                    @click="generateDueForRule(rule.id)"
                  >
                    <RefreshCw />
                  </button>
                  <button
                    class="icon-button"
                    type="button"
                    :disabled="!canEdit"
                    title="编辑"
                    aria-label="编辑"
                    @click="editRule(rule)"
                  >
                    <Edit3 />
                  </button>
                  <button
                    class="icon-button"
                    type="button"
                    :disabled="!canEdit || isEnded(rule) || generatingAll || generatingRuleId !== null"
                    :title="isEnded(rule) ? '已结束' : rule.enabled ? '停用' : '启用'"
                    :aria-label="isEnded(rule) ? '已结束' : rule.enabled ? '停用' : '启用'"
                    @click="toggleEnabled(rule)"
                  >
                    <Power v-if="rule.enabled" />
                    <PowerOff v-else />
                  </button>
                  <button
                    class="icon-button danger"
                    type="button"
                    :disabled="!canEdit || generatingAll || generatingRuleId !== null"
                    title="删除"
                    aria-label="删除"
                    @click="deleteRule(rule.id)"
                  >
                    <Trash2 />
                  </button>
                </div>
              </div>
            </article>
          </div>
        </section>

        <form ref="formRef" class="recurring-form" @submit.prevent="submit">
          <div class="form-heading">
            <div>
              <span class="pane-kicker">{{ editingId ? '编辑' : '新建' }}</span>
              <h3>{{ editingId ? '调整规则内容' : '创建周期规则' }}</h3>
            </div>
            <span class="form-mode">{{ form.type === 'INCOME' ? '收入' : form.type === 'TRANSFER' ? '转账' : '支出' }}</span>
          </div>

          <div class="form-grid compact-form-grid">
            <div class="form-field full">
              <label for="recurring-name">名称</label>
              <input id="recurring-name" v-model.trim="form.name" class="input" required placeholder="例如 房租 / 工资" />
            </div>
            <div class="form-field">
              <label for="recurring-amount">金额</label>
              <input id="recurring-amount" v-model="form.amount" class="input num" type="number" min="0.01" step="0.01" required />
            </div>
            <div class="form-field">
              <label for="recurring-type">类型</label>
              <select id="recurring-type" v-model="form.type" class="select" @change="onTypeChange">
                <option value="EXPENSE">支出</option>
                <option value="INCOME">收入</option>
                <option value="TRANSFER">转账</option>
              </select>
            </div>
            <div class="form-field">
              <label for="recurring-day">每月几日</label>
              <input id="recurring-day" v-model.number="form.dayOfMonth" class="input" type="number" min="1" max="31" required />
            </div>
            <div class="form-field">
              <label for="recurring-start-date">开始日期</label>
              <input id="recurring-start-date" v-model="form.startDate" class="input" type="date" required />
            </div>
            <div class="form-field">
              <label for="recurring-end-date">结束日期</label>
              <input id="recurring-end-date" v-model="form.endDate" class="input" type="date" />
            </div>
            <div v-if="form.type !== 'TRANSFER'" class="form-field">
              <label for="recurring-account">账户</label>
              <select id="recurring-account" v-model="form.accountId" class="select">
                <option value="">未指定账户</option>
                <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
                  {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
                </option>
              </select>
            </div>
            <template v-else>
              <div class="form-field">
                <label for="recurring-from-account">转出账户</label>
                <select id="recurring-from-account" v-model="form.accountId" class="select" required>
                  <option value="">请选择转出账户</option>
                  <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
                    {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
                  </option>
                </select>
              </div>
              <div class="form-field">
                <label for="recurring-to-account">转入账户</label>
                <select id="recurring-to-account" v-model="form.counterAccountId" class="select" required>
                  <option value="">请选择转入账户</option>
                  <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
                    {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
                  </option>
                </select>
              </div>
            </template>
            <div v-if="form.type !== 'TRANSFER'" class="form-field full">
              <label for="recurring-category">分类</label>
              <CategorySelector
                input-id="recurring-category"
                v-model="form.categoryId"
                :categories="flatFormCategories"
                :allow-empty="false"
                placeholder="请选择分类"
                search-placeholder="搜索分类名称或父级..."
              />
            </div>
            <div class="form-field">
              <label for="recurring-merchant">商户</label>
              <input id="recurring-merchant" v-model.trim="form.parsedMerchant" class="input" placeholder="可选" />
            </div>
            <div class="form-field">
              <label for="recurring-related-user">相关人员</label>
              <input id="recurring-related-user" v-model.trim="form.relatedUser" class="input" placeholder="可选" />
            </div>
            <div class="form-field full">
              <label for="recurring-description">描述</label>
              <input id="recurring-description" v-model.trim="form.description" class="input" required />
            </div>
            <div class="form-field full">
              <label for="recurring-note">备注</label>
              <textarea id="recurring-note" v-model.trim="form.note" class="textarea" placeholder="可选" />
            </div>
            <div class="form-field full">
              <span id="recurring-tags-label" class="field-label">标签</span>
              <TagSelector labelledby="recurring-tags-label" :tags="tags" :selected-ids="selectedTagIds" @update:selected-ids="selectedTagIds = $event" />
            </div>
          </div>

          <div class="row-actions form-actions">
            <button class="button" type="submit" :disabled="saving || !canEdit">
              <Plus v-if="!editingId" />
              {{ saving ? '保存中...' : editingId ? '保存规则' : '新增规则' }}
            </button>
            <button v-if="editingId" class="button secondary" type="button" @click="resetForm()">取消编辑</button>
            <button class="button ghost" type="button" @click="$emit('update:modelValue', false)">关闭</button>
          </div>
        </form>
      </div>
    </div>
  </UiModal>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Edit3, Plus, Power, PowerOff, RefreshCw, Trash2 } from 'lucide-vue-next'
import { accountsApi, categoriesApi, recurringBillsApi, tagsApi } from '@/api'
import CategorySelector from '@/components/CategorySelector.vue'
import TagSelector from '@/components/TagSelector.vue'
import UiModal from '@/components/UiModal.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useMessage } from '@/composables/useMessage'
import { flattenCategories } from '@/utils/category'
import { defaultAccountIcon, formatDate, formatMoney, today, toNumber } from '@/utils/format'
import type { Account, Category, GenerateRecurringBillsResult, RecurringBill, SaveRecurringBillRequest, Tag } from '@/types'

const props = defineProps<{
  modelValue: boolean
  canEdit: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  generated: []
}>()

const { confirm } = useConfirm()
const loading = ref(false)
const saving = ref(false)
const generatingAll = ref(false)
const generatingRuleId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const rules = ref<RecurringBill[]>([])
const accounts = ref<Account[]>([])
const tags = ref<Tag[]>([])
const allCategories = ref<Category[]>([])
const formCategories = ref<Category[]>([])
const selectedTagIds = ref<number[]>([])
const formRef = ref<HTMLFormElement | null>(null)
// 弹窗内命令栏内嵌展示（.recurring-status），不走页面级横幅
const { message, show, showSuccess, showInfo, showError, clear } = useMessage()

const form = reactive({
  name: '',
  enabled: true,
  amount: '',
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME' | 'TRANSFER',
  description: '',
  categoryId: '' as number | '',
  accountId: '' as number | '',
  counterAccountId: '' as number | '',
  parsedMerchant: '',
  relatedUser: '',
  note: '',
  dayOfMonth: Number(today().slice(-2)),
  startDate: today(),
  endDate: ''
})

const activeAccounts = computed(() => accounts.value.filter(account => account.active))
const flatAllCategories = computed(() => flattenCategories(allCategories.value))
const flatFormCategories = computed(() => flattenCategories(formCategories.value))
const activeRuleCount = computed(() => rules.value.filter(rule => rule.enabled).length)
const dueRuleCount = computed(() => {
  const current = today()
  return rules.value.filter(rule => rule.enabled && rule.nextRunDate <= current).length
})

watch(
  () => props.modelValue,
  open => {
    if (open) {
      resetForm(false)
      void loadAll()
    }
  }
)

async function loadAll(): Promise<void> {
  loading.value = true
  clear()
  try {
    const [ruleResult, accountResult, tagResult, categoryResult] = await Promise.all([
      recurringBillsApi.list(),
      accountsApi.list(),
      tagsApi.list(),
      categoriesApi.list()
    ])
    rules.value = ruleResult
    accounts.value = accountResult
    tags.value = tagResult
    allCategories.value = categoryResult
    await loadFormCategories()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

async function loadRules(): Promise<void> {
  rules.value = await recurringBillsApi.list()
}

async function loadFormCategories(): Promise<void> {
  formCategories.value = await categoriesApi.list(form.type)
}

async function onTypeChange(): Promise<void> {
  form.categoryId = ''
  if (form.type === 'TRANSFER') {
    formCategories.value = []
    return
  }
  await loadFormCategories()
}

async function submit(): Promise<void> {
  if (!props.canEdit) return
  if (!form.name || !form.description || toNumber(form.amount) <= 0) {
    show('error', '请填写名称、金额和描述。')
    return
  }
  if (form.type === 'TRANSFER') {
    if (form.accountId === '' || form.counterAccountId === '') {
      show('error', '转账需选择转出和转入账户。')
      return
    }
    if (Number(form.accountId) === Number(form.counterAccountId)) {
      show('error', '转出和转入账户不能相同。')
      return
    }
  } else if (!form.categoryId) {
    show('error', '请填写分类。')
    return
  }
  if (form.dayOfMonth < 1 || form.dayOfMonth > 31) {
    show('error', '每月生成日必须在 1-31 之间。')
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await recurringBillsApi.update(editingId.value, payload)
      showSuccess('周期账单已更新。')
    } else {
      await recurringBillsApi.create(payload)
      showSuccess('周期账单已创建。')
    }
    await loadRules()
    resetForm()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

function buildPayload(): SaveRecurringBillRequest {
  const isTransfer = form.type === 'TRANSFER'
  return {
    name: form.name,
    enabled: form.enabled,
    amount: form.amount,
    type: form.type,
    description: form.description,
    categoryId: isTransfer ? null : Number(form.categoryId),
    accountId: form.accountId === '' ? null : Number(form.accountId),
    counterAccountId: isTransfer && form.counterAccountId !== '' ? Number(form.counterAccountId) : null,
    parsedMerchant: form.parsedMerchant || null,
    relatedUser: form.relatedUser || null,
    note: form.note || null,
    frequency: 'MONTHLY',
    dayOfMonth: Number(form.dayOfMonth),
    startDate: form.startDate,
    endDate: form.endDate || null,
    tagIds: selectedTagIds.value
  }
}

async function editRule(rule: RecurringBill): Promise<void> {
  editingId.value = rule.id
  Object.assign(form, {
    name: rule.name,
    enabled: rule.enabled,
    amount: String(rule.amount),
    type: rule.type,
    description: rule.description,
    categoryId: rule.categoryId ?? '',
    accountId: rule.accountId ?? '',
    counterAccountId: rule.counterAccountId ?? '',
    parsedMerchant: rule.parsedMerchant || '',
    relatedUser: rule.relatedUser || '',
    note: rule.note || '',
    dayOfMonth: rule.dayOfMonth,
    startDate: rule.startDate,
    endDate: rule.endDate || ''
  })
  selectedTagIds.value = rule.tags?.map(tag => tag.id) || []
  scrollToFormOnMobile()
  if (rule.type === 'TRANSFER') {
    formCategories.value = []
  } else {
    await loadFormCategories()
  }
}

function scrollToFormOnMobile(): void {
  if (!window.matchMedia('(max-width: 820px)').matches) return
  void nextTick(() => {
    const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
    formRef.value?.scrollIntoView({ behavior, block: 'start' })
  })
}

async function toggleEnabled(rule: RecurringBill): Promise<void> {
  if (!props.canEdit) return
  try {
    await recurringBillsApi.setEnabled(rule.id, !rule.enabled)
    await loadRules()
    showSuccess(rule.enabled ? '周期账单已停用。' : '周期账单已启用。')
  } catch (error) {
    showError(error)
  }
}

async function deleteRule(id: number): Promise<void> {
  if (!props.canEdit) return
  const confirmed = await confirm({
    title: '删除周期账单',
    message: '确认删除这条周期账单规则？已生成的交易不会被删除。',
    confirmText: '删除',
    danger: true
  })
  if (!confirmed) return
  try {
    await recurringBillsApi.delete(id)
    await loadRules()
    if (editingId.value === id) resetForm()
    showSuccess('周期账单已删除。')
  } catch (error) {
    showError(error)
  }
}

async function generateDue(): Promise<void> {
  if (!props.canEdit) return
  if (generatingAll.value || generatingRuleId.value !== null) return
  generatingAll.value = true
  try {
    const result = await recurringBillsApi.generateDue()
    await loadRules()
    showGenerateResult(result)
    if (result.generatedCount > 0) emit('generated')
  } catch (error) {
    showError(error)
  } finally {
    generatingAll.value = false
  }
}

async function generateDueForRule(id: number): Promise<void> {
  if (!props.canEdit) return
  if (generatingAll.value || generatingRuleId.value !== null) return
  generatingRuleId.value = id
  try {
    const result = await recurringBillsApi.generateDueForBill(id)
    await loadRules()
    showGenerateResult(result)
    if (result.generatedCount > 0) emit('generated')
  } catch (error) {
    showError(error)
  } finally {
    generatingRuleId.value = null
  }
}

function showGenerateResult(result: GenerateRecurringBillsResult): void {
  if (result.truncated) {
    showInfo(
      result.generatedCount > 0
        ? `已生成 ${result.generatedCount} 笔交易，还有到期账单未处理。`
        : '还有到期账单未处理。'
    )
  } else if (result.generatedCount > 0) {
    showSuccess(`已生成 ${result.generatedCount} 笔交易。`)
  } else if (result.skippedCount > 0) {
    showInfo('到期交易已存在，没有重复生成。')
  } else {
    showInfo('目前没有到期的周期账单。')
  }
}

function resetForm(loadCategories = true): void {
  editingId.value = null
  Object.assign(form, {
    name: '',
    enabled: true,
    amount: '',
    type: 'EXPENSE' as 'EXPENSE' | 'INCOME' | 'TRANSFER',
    description: '',
    categoryId: '',
    accountId: '',
    counterAccountId: '',
    parsedMerchant: '',
    relatedUser: '',
    note: '',
    dayOfMonth: Number(today().slice(-2)),
    startDate: today(),
    endDate: ''
  })
  selectedTagIds.value = []
  if (loadCategories) {
    void loadFormCategories()
  }
}

function categoryLabel(categoryId?: number | null): string {
  if (!categoryId) return '未分类'
  const category = flatAllCategories.value.find(item => item.id === categoryId)
  if (!category) return '未分类'
  return category.parentName ? `${category.parentName} / ${category.name}` : category.name
}

function accountLabel(accountId?: number | null): string {
  if (!accountId) return '未指定账户'
  const account = accounts.value.find(item => item.id === accountId)
  if (!account) return '未知账户'
  return `${account.icon || defaultAccountIcon(account.type)} ${account.name}`
}

function isEnded(rule: RecurringBill): boolean {
  return Boolean(rule.endDate && rule.endDate < today())
}

function ruleStatusLabel(rule: RecurringBill): string {
  if (isEnded(rule)) return '已结束'
  return rule.enabled ? '启用' : '停用'
}

function ruleStatusClass(rule: RecurringBill): string {
  if (isEnded(rule)) return 'ended'
  return rule.enabled ? 'on' : 'off'
}
</script>

<style scoped>
.recurring-shell {
  display: grid;
  gap: 1rem;
}

.recurring-command {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 0.85rem;
  border: 1px solid var(--color-border-soft);
  border-radius: 8px;
  background: var(--color-surface-soft);
}

.recurring-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.7rem;
  min-width: 0;
}

.recurring-metrics span {
  display: inline-flex;
  align-items: baseline;
  gap: 0.25rem;
  color: var(--color-muted);
  font-size: 0.86rem;
}

.recurring-metrics strong {
  color: var(--color-text);
  font-family: var(--font-num);
  font-size: 1.08rem;
}

.command-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  min-width: 0;
}

.recurring-status {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.9rem;
  white-space: nowrap;
}

.recurring-status.success {
  color: var(--color-success-text);
}

.recurring-status.error {
  color: var(--color-danger-text);
}

.recurring-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 1.25rem;
  align-items: start;
}

.rules-pane {
  min-width: 0;
}

.pane-heading,
.form-heading,
.rule-title-row,
.rule-actions {
  display: flex;
  align-items: center;
}

.pane-heading,
.form-heading {
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
}

.pane-heading h3,
.form-heading h3 {
  margin: 0.1rem 0 0;
  font-size: 1rem;
  line-height: 1.25;
}

.pane-kicker {
  display: block;
  color: var(--color-muted);
  font-size: 0.78rem;
  font-weight: 700;
}

.recurring-list {
  display: grid;
  gap: 0.7rem;
  max-height: min(56vh, 520px);
  overflow: auto;
  padding-right: 0.2rem;
}

.recurring-item {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  gap: 0.8rem;
  align-items: center;
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fffefb;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    opacity 0.16s ease;
}

.recurring-item.selected {
  border-color: rgba(235, 94, 40, 0.55);
  box-shadow: 0 0 0 3px rgba(235, 94, 40, 0.09);
}

.recurring-item.inactive {
  opacity: 0.72;
}

.recurring-item.ended .rule-date {
  background: #eee6d8;
}

.recurring-item.ended .rule-date strong {
  color: var(--color-muted);
}

.rule-date {
  display: grid;
  place-items: center;
  align-self: stretch;
  min-height: 68px;
  border-radius: 8px;
  background: #f4ead9;
}

.rule-date span {
  color: var(--color-muted);
  font-size: 0.72rem;
  font-weight: 700;
}

.rule-date strong {
  color: var(--color-primary-dark);
  font-family: var(--font-num);
  font-size: 1.65rem;
  line-height: 1;
}

.rule-main {
  min-width: 0;
}

.rule-title-row {
  justify-content: flex-start;
  gap: 0.5rem;
  min-width: 0;
}

.rule-title-row strong {
  overflow: hidden;
  font-size: 0.98rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-badge {
  flex: none;
  padding: 0.08rem 0.42rem;
  border-radius: 999px;
  font-size: 0.75rem;
  line-height: 1.4;
}

.rule-badge.on {
  background: rgba(47, 156, 99, 0.12);
  color: var(--color-success-text);
}

.rule-badge.off {
  background: rgba(117, 117, 117, 0.12);
  color: var(--color-muted);
}

.rule-badge.ended {
  background: rgba(149, 135, 111, 0.14);
  color: #786b57;
}

.rule-meta,
.rule-schedule {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.65rem;
  margin-top: 0.32rem;
  color: var(--color-muted);
  font-size: 0.84rem;
}

.rule-side {
  display: grid;
  justify-items: end;
  gap: 0.55rem;
}

.rule-amount {
  font-family: var(--font-num);
  font-size: 1.05rem;
  font-weight: 700;
  white-space: nowrap;
}

.rule-amount.income {
  color: var(--color-success-text);
}

.rule-amount.expense {
  color: var(--color-primary-text);
}

.rule-amount.transfer {
  color: var(--color-muted);
}

.rule-actions {
  gap: 0.35rem;
  justify-content: flex-end;
}

.icon-button {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fffaf1;
  color: var(--color-text);
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    color 0.16s ease;
}

.icon-button:hover:not(:disabled) {
  border-color: rgba(235, 94, 40, 0.45);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.icon-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.icon-button.danger {
  color: var(--color-danger-text);
}

.icon-button svg,
.button svg {
  width: 16px;
  height: 16px;
}

.recurring-form {
  min-width: 0;
  padding-left: 1.25rem;
  border-left: 1px solid var(--color-border-soft);
}

.form-mode {
  flex: none;
  padding: 0.2rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: #fffefb;
  color: var(--color-muted);
  font-size: 0.8rem;
  font-weight: 700;
}

.compact-form-grid {
  gap: 0.8rem;
}

.compact-form-grid .textarea {
  min-height: 84px;
}

.pane-state {
  min-height: 220px;
}

.form-actions {
  justify-content: flex-end;
  margin-top: 1rem;
}

/* 与全局移动断点 820px 对齐（原 980/720 两档合并为一档，行为取最接近的档位） */
@media (max-width: 820px) {
  .recurring-workbench {
    grid-template-columns: 1fr;
  }

  .recurring-form {
    padding-top: 1rem;
    padding-left: 0;
    border-top: 1px solid var(--color-border-soft);
    border-left: 0;
  }

  .recurring-command,
  .command-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .command-actions {
    gap: 0.55rem;
  }

  .recurring-status {
    white-space: normal;
  }

  .recurring-metrics {
    justify-content: space-between;
  }

  .recurring-item {
    grid-template-columns: 48px minmax(0, 1fr);
    align-items: start;
  }

  .rule-date {
    min-height: 58px;
  }

  .rule-side {
    grid-column: 2;
    justify-items: start;
  }

  .rule-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .rule-actions .icon-button {
    width: 44px;
    height: 44px;
  }

  .compact-form-grid {
    grid-template-columns: 1fr;
  }

  .form-actions {
    justify-content: stretch;
  }

  .form-actions .button {
    flex: 1;
  }
}
</style>
