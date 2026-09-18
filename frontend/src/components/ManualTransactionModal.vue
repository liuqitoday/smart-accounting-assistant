<template>
  <UiModal
    :model-value="modelValue"
    title="手动记一笔"
    subtitle="不想打字描述,直接填表单也行。"
    size="lg"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="manual-error-wrap t-input-wrap" :class="{ 'is-error': errorActive }">
      <p class="message error manual-error-message t-error-msg">{{ errorText }}</p>

      <form
        ref="formRef"
        class="form-grid manual-form t-input"
        :class="{ 'is-error': errorActive, 'is-shaking': formShaking }"
        @input="clearFormError"
        @submit.prevent="submit"
      >
        <div class="form-field">
          <label for="manual-amount">金额</label>
          <input id="manual-amount" v-model="form.amount" class="input num" type="number" step="0.01" min="0.01" required />
        </div>
        <div class="form-field">
          <label for="manual-type">类型</label>
          <select id="manual-type" v-model="form.type" class="select" @change="onTypeChange">
            <option value="EXPENSE">支出</option>
            <option value="INCOME">收入</option>
            <option value="TRANSFER">转账</option>
          </select>
        </div>
        <div class="form-field">
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
          <div class="form-field">
            <label for="manual-account">账户</label>
            <select id="manual-account" v-model="form.accountId" class="select">
              <option value="">未指定账户</option>
              <option v-for="account in activeAccounts" :key="account.id" :value="account.id">
                {{ account.icon || defaultAccountIcon(account.type) }} {{ account.name }}
              </option>
            </select>
          </div>
          <div class="form-field">
            <label for="manual-merchant">商户</label>
            <input id="manual-merchant" v-model.trim="form.parsedMerchant" class="input" placeholder="可选" />
          </div>
          <div class="form-field">
            <label for="manual-related-user">相关人员</label>
            <input id="manual-related-user" v-model.trim="form.relatedUser" class="input" placeholder="可选" />
          </div>
          <div class="form-field full">
            <label for="manual-description">描述</label>
            <input id="manual-description" v-model.trim="form.description" class="input" required />
          </div>
        </template>

        <div class="form-field full">
          <label for="manual-note">备注</label>
          <textarea id="manual-note" v-model.trim="form.note" class="textarea" placeholder="可选" />
        </div>
        <div v-if="!isTransfer" class="form-field full">
          <span id="manual-tags-label" class="field-label">标签</span>
          <TagSelector labelledby="manual-tags-label" :tags="tags" :selected-ids="selectedTagIds" @update:selected-ids="selectedTagIds = $event" />
        </div>
        <div class="form-field full row-actions">
          <button class="button" type="submit" :disabled="saving">
            <span v-if="saving" class="t-shimmer button-shimmer" data-text="保存中...">保存中...</span>
            <template v-else>{{ isTransfer ? '确认转账' : '记下这一笔' }}</template>
          </button>
          <button class="button secondary" type="button" @click="$emit('update:modelValue', false)">取消</button>
        </div>
      </form>
    </div>
  </UiModal>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { categoriesApi, transactionsApi, transfersApi } from '@/api'
import { ApiError } from '@/api/http'
import CategorySelector from '@/components/CategorySelector.vue'
import TagSelector from '@/components/TagSelector.vue'
import UiModal from '@/components/UiModal.vue'
import type { Account, Category, SaveTransactionRequest, Tag, TransactionType } from '@/types'
import { resolveDefaultAccountId } from '@/utils/account'
import { flattenCategories } from '@/utils/category'
import { cssMs } from '@/utils/css'
import { defaultAccountIcon, today, toNumber } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  accounts: Account[]
  tags: Tag[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({
  amount: '',
  type: 'EXPENSE' as TransactionType,
  transactionDate: today(),
  categoryId: '' as number | '',
  accountId: '' as number | '',
  fromAccountId: '' as number | '',
  toAccountId: '' as number | '',
  parsedMerchant: '',
  relatedUser: '',
  description: '',
  note: ''
})
const selectedTagIds = ref<number[]>([])
const categories = ref<Category[]>([])
const saving = ref(false)
const errorText = ref('')
const errorActive = ref(false)
const formShaking = ref(false)
const formRef = ref<HTMLFormElement | null>(null)
let revertTimer: number | undefined
let shakeTimer: number | undefined

const isTransfer = computed(() => form.type === 'TRANSFER')
const flatCategories = computed(() => flattenCategories(categories.value))
const activeAccounts = computed(() => props.accounts.filter(account => account.active))

watch(
  () => props.modelValue,
  open => {
    if (open) {
      resetForm()
      void loadCategories()
    }
  }
)

onBeforeUnmount(() => {
  clearErrorTimers()
})

function resetForm(): void {
  Object.assign(form, {
    amount: '',
    type: 'EXPENSE' as TransactionType,
    transactionDate: today(),
    categoryId: '',
    accountId: resolveDefaultAccountId(props.accounts) ?? '',
    fromAccountId: '',
    toAccountId: '',
    parsedMerchant: '',
    relatedUser: '',
    description: '',
    note: ''
  })
  selectedTagIds.value = []
  clearFormError()
}

async function onTypeChange(): Promise<void> {
  form.categoryId = ''
  await loadCategories()
}

async function loadCategories(): Promise<void> {
  if (isTransfer.value) {
    categories.value = []
    return
  }
  try {
    categories.value = await categoriesApi.list(form.type)
  } catch (error) {
    showError(error)
  }
}

async function submit(): Promise<void> {
  if (isTransfer.value) {
    await submitTransfer()
    return
  }
  if (!form.categoryId || toNumber(form.amount) <= 0 || !form.description) {
    showFormError('请填写金额、分类和描述后再保存。')
    return
  }
  saving.value = true
  clearFormError()
  try {
    const payload: SaveTransactionRequest = {
      amount: form.amount,
      type: form.type as Exclude<TransactionType, 'TRANSFER'>,
      description: form.description,
      originalText: form.description,
      categoryId: form.categoryId,
      transactionDate: form.transactionDate,
      parsedMerchant: form.parsedMerchant || null,
      relatedUser: form.relatedUser || null,
      note: form.note || null,
      accountId: form.accountId === '' ? null : Number(form.accountId),
      confidenceScore: null,
      aiModelUsed: null,
      aiSuggestedCategoryId: null
    }
    const saved = await transactionsApi.save(payload)
    if (saved.id && selectedTagIds.value.length) {
      try {
        await transactionsApi.updateTags(saved.id, selectedTagIds.value)
      } catch {
        // 交易已落库,标签关联失败不阻断关闭,避免重复提交造成重复记账
      }
    }
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function submitTransfer(): Promise<void> {
  if (form.fromAccountId === '' || form.toAccountId === '') {
    showFormError('请选择转出和转入账户。')
    return
  }
  if (form.fromAccountId === form.toAccountId) {
    showFormError('转出和转入账户不能相同。')
    return
  }
  if (toNumber(form.amount) <= 0) {
    showFormError('请输入大于 0 的转账金额。')
    return
  }
  saving.value = true
  clearFormError()
  try {
    await transfersApi.create({
      fromAccountId: Number(form.fromAccountId),
      toAccountId: Number(form.toAccountId),
      amount: form.amount,
      transferDate: form.transactionDate,
      note: form.note || null
    })
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}


function showError(error: unknown): void {
  showFormError(error instanceof ApiError ? error.message : '操作失败,请稍后重试')
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
