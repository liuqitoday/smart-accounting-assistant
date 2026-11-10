<template>
  <component
    :is="embedded ? 'div' : UiModal"
    :model-value="modelValue"
    title="手动记一笔"
    size="lg"
    @update:model-value="requestClose"
  >
    <div class="manual-error-wrap t-input-wrap" :class="{ 'is-error': errorActive }">
      <p v-if="errorText" class="message error" role="alert">{{ errorText }}</p>
      <p v-if="!online" class="message info">{{ isTransfer ? '转账需要联网，请保留草稿稍后提交。' : '当前离线，保存后会在联网时自动同步。' }}</p>

      <form
        ref="formRef"
        class="form-grid record-form manual-form t-input"
        :class="{ 'is-error': errorActive, 'is-shaking': formShaking }"
        @input="clearFormError"
        @submit.prevent="submit"
      >
        <fieldset class="manual-fields" :disabled="saving || !canEdit">
        <div class="form-field">
          <label for="manual-amount">金额</label>
          <input id="manual-amount" ref="amountInput" inputmode="decimal" v-model="form.amount" class="input num" type="number" step="0.01" min="0.01" required />
        </div>
        <div class="form-field">
          <label for="manual-type">类型</label>
          <select id="manual-type" v-model="form.type" class="select" @change="onTypeChange">
            <option value="EXPENSE">支出</option>
            <option value="INCOME">收入</option>
            <option value="TRANSFER">转账</option>
          </select>
        </div>
        <div class="form-field" :class="{ full: isTransfer }">
          <label for="manual-date">日期</label>
          <input id="manual-date" v-model="form.transactionDate" class="input" type="date" required />
        </div>

        <!-- 转账：转出 / 转入账户 -->
        <template v-if="isTransfer">
          <div class="form-field">
            <label for="manual-from-account">转出账户</label>
            <select id="manual-from-account" v-model="form.fromAccountId" class="select" required>
              <option disabled value="">请选择转出账户</option>
              <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
                {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
              </option>
            </select>
          </div>
          <div class="form-field">
            <label for="manual-to-account">转入账户</label>
            <select id="manual-to-account" v-model="form.toAccountId" class="select" required>
              <option disabled value="">请选择转入账户</option>
              <option
                v-for="account in activeAccounts"
                :key="account.id"
                :value="account.id"
                :disabled="account.id === form.fromAccountId"
              >
                {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
              </option>
            </select>
          </div>
        </template>

        <!-- 收入 / 支出：分类、账户、商户、描述、标签 -->
        <template v-else>
          <div class="form-field">
            <label for="manual-account">账户</label>
            <select id="manual-account" v-model="form.accountId" class="select" @change="draft.accountInitialized = true">
              <option value="">未指定账户</option>
              <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
                {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
              </option>
            </select>
          </div>
          <div class="form-field full">
            <label for="manual-category">分类</label>
            <CategorySelector
              input-id="manual-category"
              v-model="form.categoryId"
              :categories="flatCategories"
              :allow-empty="false"
              placeholder="请选择分类"
              search-placeholder="搜索分类名称或父级..."
            />
            <p v-if="!flatCategories.length" class="muted-text">该类型暂无分类</p>
          </div>
          <div class="form-field full">
            <label for="manual-description">描述</label>
            <input id="manual-description" v-model.trim="form.description" class="input" placeholder="可选，默认使用分类名称" />
          </div>
        </template>
        <div class="t-acc full" :data-open="String(draft.moreOpen)">
          <button class="t-acc-head manual-more" type="button" :aria-expanded="draft.moreOpen" aria-controls="manual-extras"
            @click="draft.moreOpen = !draft.moreOpen">
            <span>补充信息</span><span class="manual-more-hint">{{ extraCount ? `已填 ${extraCount} 项` : isTransfer ? '备注' : '商户、备注、标签' }}</span><ChevronDown class="t-acc-chevron" />
          </button>
          <div id="manual-extras" class="t-acc-panel" :aria-hidden="!draft.moreOpen" :inert="draft.moreOpen ? undefined : true">
            <div class="t-acc-panel-inner"><div class="form-grid record-form manual-extras">
              <template v-if="!isTransfer">
                <div class="form-field"><label for="manual-merchant">商户</label><input id="manual-merchant" v-model.trim="form.parsedMerchant" class="input" placeholder="可选" /></div>
                <div class="form-field"><label for="manual-related-user">相关人员</label><input id="manual-related-user" v-model.trim="form.relatedUser" class="input" placeholder="可选" /></div>
              </template>
              <div class="form-field full"><label for="manual-note">备注</label><textarea id="manual-note" v-model.trim="form.note" class="textarea" placeholder="可选" /></div>
              <div v-if="!isTransfer" class="form-field full">
                <span id="manual-tags-label" class="field-label">标签</span>
                <TagSelector labelledby="manual-tags-label" :tags="tags" :selected-ids="draft.tagIds" @update:selected-ids="draft.tagIds = $event" />
              </div>
            </div></div>
          </div>
        </div>
        </fieldset>
        <div class="form-field full record-save-actions">
          <button class="button" type="submit" :disabled="saving || !canEdit || (isTransfer && !online)">
            <span v-if="saving" class="t-shimmer button-shimmer" data-text="保存中...">保存中...</span>
            <template v-else>{{ isTransfer ? '确认转账' : online ? '记下这一笔' : '保存，联网后同步' }}</template>
          </button>
          <button v-if="!embedded" class="button secondary" type="button" :disabled="saving" @click="requestClose(false)">稍后再记</button>
        </div>
      </form>
    </div>
  </component>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { categoriesApi, transfersApi } from '@/api'
import { ChevronDown } from 'lucide-vue-next'
import { useDraft } from '@/composables/useDraft'
import { useConnectivity } from '@/composables/useConnectivity'
import { useLedgerStore } from '@/stores/ledger'
import { saveOrQueueTransaction } from '@/stores/outbox'
import { identifySubmission, type SubmissionIdentity } from '@/utils/submission'
import CategorySelector from '@/components/CategorySelector.vue'
import TagSelector from '@/components/TagSelector.vue'
import UiModal from '@/components/UiModal.vue'
import type { AccountOption, Category, SaveTransactionRequest, Tag, TransactionType } from '@/types'
import { resolveDefaultAccountId } from '@/utils/account'
import { flattenCategories } from '@/utils/category'
import { cssMs } from '@/utils/css'
import { defaultAccountIcon, today, toNumber } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  accounts: AccountOption[]
  embedded?: boolean
  tags: Tag[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [result?: { queued: boolean }]
  busy: [value: boolean]
}>()

const ledger = useLedgerStore()
const ledgerId = ledger.state.activeLedger?.id
const { online } = useConnectivity()
const { draft, clear } = useDraft('manual-transaction', {
  form: {
    amount: '', type: 'EXPENSE' as TransactionType, transactionDate: today(),
    categoryId: '' as number | '', accountId: '' as number | '', fromAccountId: '' as number | '', toAccountId: '' as number | '',
    parsedMerchant: '', relatedUser: '', description: '', note: ''
  },
  tagIds: [] as number[], moreOpen: false, accountInitialized: false,
  submission: { fingerprint: '', key: '' } as SubmissionIdentity
})
const form = computed(() => draft.value.form)
const canEdit = computed(() => ledger.canEdit.value && ledger.state.activeLedger?.id === ledgerId)
const amountInput = ref<HTMLInputElement | null>(null)
const categories = ref<Category[]>([])
const saving = ref(false)
const errorText = ref('')
const errorActive = ref(false)
const formShaking = ref(false)
const formRef = ref<HTMLFormElement | null>(null)
let revertTimer: number | undefined
let shakeTimer: number | undefined

const isTransfer = computed(() => form.value.type === 'TRANSFER')
const extraCount = computed(() => (isTransfer.value
  ? [form.value.note]
  : [form.value.parsedMerchant, form.value.relatedUser, form.value.note, draft.value.tagIds.length]
).filter(Boolean).length)
const flatCategories = computed(() => flattenCategories(categories.value))
const activeAccounts = computed(() => props.accounts.filter(account => account.active))

watch(
  () => props.modelValue,
  open => {
    if (open) {
      applyDefaultAccount()
      void loadCategories()
      void nextTick(() => amountInput.value?.focus({ preventScroll: true }))
    }
  }, { immediate: true }
)
watch(() => props.accounts, applyDefaultAccount)
watch(saving, value => emit('busy', value))

onBeforeUnmount(() => {
  clearErrorTimers()
})

function applyDefaultAccount(): void {
  if (!draft.value.accountInitialized && props.accounts.length) {
    form.value.accountId = resolveDefaultAccountId(props.accounts) ?? ''
    draft.value.accountInitialized = true
  }
}

function requestClose(value: boolean): void {
  if (!saving.value) emit('update:modelValue', value)
}

async function onTypeChange(): Promise<void> {
  form.value.categoryId = ''
  await loadCategories()
}

async function loadCategories(): Promise<void> {
  if (isTransfer.value) {
    categories.value = []
    return
  }
  try {
    const type = form.value.type
    const result = await categoriesApi.list(type)
    if (form.value.type === type) categories.value = result
  } catch (error) {
    showError(error)
  }
}

async function submit(): Promise<void> {
  if (saving.value || !canEdit.value || !ledgerId) return
  if (isTransfer.value) {
    await submitTransfer()
    return
  }
  if (!form.value.categoryId || !Number.isFinite(Number(form.value.amount)) || toNumber(form.value.amount) <= 0) {
    showFormError('请填写金额和分类后再保存。')
    return
  }
  saving.value = true
  clearFormError()
  try {
    const payload: SaveTransactionRequest = {
      amount: form.value.amount,
      type: form.value.type as Exclude<TransactionType, 'TRANSFER'>,
      description: form.value.description || flatCategories.value.find(category => category.id === form.value.categoryId)?.name || '日常记账',
      originalText: form.value.description || flatCategories.value.find(category => category.id === form.value.categoryId)?.name || '手动记账',
      categoryId: form.value.categoryId,
      transactionDate: form.value.transactionDate,
      parsedMerchant: form.value.parsedMerchant || null,
      relatedUser: form.value.relatedUser || null,
      note: form.value.note || null,
      accountId: form.value.accountId === '' ? null : Number(form.value.accountId),
      confidenceScore: null,
      aiModelUsed: null,
      aiSuggestedCategoryId: null,
      tagIds: draft.value.tagIds
    }
    const result = await saveOrQueueTransaction(identifySubmission(payload, draft.value.submission), ledgerId)
    clear()
    if (ledger.state.activeLedger?.id === ledgerId) {
      emit('update:modelValue', false)
      emit('saved', result)
    }
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function submitTransfer(): Promise<void> {
  if (!online.value) { showFormError('转账需要联网，请稍后再试。'); return }
  if (form.value.fromAccountId === '' || form.value.toAccountId === '') {
    showFormError('请选择转出和转入账户。')
    return
  }
  if (form.value.fromAccountId === form.value.toAccountId) {
    showFormError('转出和转入账户不能相同。')
    return
  }
  if (toNumber(form.value.amount) <= 0) {
    showFormError('请输入大于 0 的转账金额。')
    return
  }
  saving.value = true
  clearFormError()
  try {
    await transfersApi.create({
      fromAccountId: Number(form.value.fromAccountId),
      toAccountId: Number(form.value.toAccountId),
      amount: form.value.amount,
      transferDate: form.value.transactionDate,
      note: form.value.note || null
    })
    clear()
    if (ledger.state.activeLedger?.id === ledgerId) {
      emit('update:modelValue', false)
      emit('saved', { queued: false })
    }
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}


function showError(error: unknown): void {
  showFormError(error instanceof Error ? error.message : '操作失败,请稍后重试')
}

function showFormError(message: string): void {
  clearErrorTimers()
  errorText.value = message
  errorActive.value = true
  formShaking.value = false

  void nextTick(() => {
    const form = formRef.value
    if (form) {
      void form.offsetWidth
    }
    formShaking.value = true
    const shakeMs = shakeDurationMs()
    shakeTimer = window.setTimeout(() => {
      formShaking.value = false
    }, shakeMs + 20)
    revertTimer = window.setTimeout(() => {
      errorActive.value = false
    }, shakeMs + revertHoldMs())
  })
}

function clearFormError(): void {
  errorText.value = ''
  clearErrorTimers()
  errorActive.value = false
  formShaking.value = false
}

function clearErrorTimers(): void {
  if (shakeTimer !== undefined) {
    window.clearTimeout(shakeTimer)
    shakeTimer = undefined
  }
  if (revertTimer !== undefined) {
    window.clearTimeout(revertTimer)
    revertTimer = undefined
  }
}

function shakeDurationMs(): number {
  return cssMs('--shake-dur-a', 80) * 2 + cssMs('--shake-dur-b', 60) * 2
}

function revertHoldMs(): number {
  return cssMs('--revert-hold', 3000)
}
</script>

<style scoped>
.manual-fields { display: contents; }
.manual-fields > .t-acc { grid-column: 1 / -1; }
.manual-more { display: flex; gap: 8px; align-items: center; width: 100%; min-height: 44px; border: 0; background: transparent; color: var(--color-muted); padding: 8px 0; text-align: left; }
.manual-more-hint { margin-left: auto; font-size: 12px; }
.manual-more svg { flex: none; width: 16px; height: 16px; }
.manual-extras { padding-top: 12px; }
</style>
