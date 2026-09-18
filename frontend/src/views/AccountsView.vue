<template>
  <AppShell>
    <PageHeader title="账户管理" subtitle="管理当前账本下共享的现金、银行卡、网络支付和投资账户。">
      <template #actions>
        <button class="button" type="button" :disabled="!ledger.canEdit.value" @click="openCreate">
          <Plus />
          新建账户
        </button>
      </template>
    </PageHeader>

    <MessageBanner :message="message.text" :type="message.type" @dismissed="clear" />

    <section class="section-stack">
      <div class="grid cols-3">
        <div class="card stat-card">
          <span class="stat-label">账户数量</span>
          <AnimatedNumber class="stat-value" :value="accounts.length" />
        </div>
        <div class="card stat-card">
          <span class="stat-label">当前总余额</span>
          <AnimatedNumber class="stat-value" :class="totalBalance >= 0 ? 'positive' : 'negative'" :value="formatMoney(totalBalance)" />
        </div>
        <div class="card stat-card">
          <span class="stat-label">启用账户</span>
          <AnimatedNumber class="stat-value" :value="activeCount" />
        </div>
      </div>

      <div v-if="loading" class="loading-state">
        <span class="t-shimmer" data-text="加载中...">加载中...</span>
      </div>
      <div v-else-if="!accounts.length" class="empty-state coins">还没有账户,添加现金或银行卡开始吧</div>
      <div v-else class="grid cols-3">
        <article v-for="account in accounts" :key="account.id" class="card account-card" :class="{ disabled: !account.active }">
          <div class="account-head">
            <div class="account-icon" :style="{ background: account.color || '#EB5E28' }">
              {{ account.icon || defaultAccountIcon(account.type) }}
            </div>
            <div>
              <h2>
                {{ account.name }}
                <span v-if="account.default" class="badge neutral default-badge">默认</span>
              </h2>
              <p>{{ accountTypeLabel(account.type) }}</p>
            </div>
          </div>
          <div class="account-balance" :class="toNumber(account.currentBalance) >= 0 ? 'positive' : 'negative'">
            {{ formatMoney(account.currentBalance) }}
          </div>
          <div class="account-meta">
            <span>期初 {{ formatMoney(account.initialBalance) }}</span>
            <span>{{ account.transactionCount }} 笔交易</span>
          </div>
          <div class="row-actions">
            <button
              class="button secondary compact"
              type="button"
              :disabled="!ledger.canEdit.value || !account.active || settingDefaultId === account.id"
              :title="account.active ? undefined : '停用的账户不能设为默认'"
              @click="toggleDefault(account)"
            >
              <span
                v-if="settingDefaultId === account.id"
                class="t-shimmer button-shimmer"
                data-text="设置中..."
              >设置中...</span>
              <template v-else>{{ account.default ? '取消默认' : '设为默认' }}</template>
            </button>
            <button
              class="button secondary compact"
              type="button"
              :disabled="!ledger.canEdit.value || togglingId === account.id"
              @click="openEdit(account)"
            >
              编辑
            </button>
            <button
              class="button secondary compact"
              type="button"
              :disabled="!ledger.canEdit.value || togglingId === account.id"
              @click="toggleActive(account)"
            >
              {{ account.active ? '停用' : '启用' }}
            </button>
            <button
              class="button danger compact"
              type="button"
              :disabled="!ledger.canEdit.value || account.transactionCount > 0 || togglingId === account.id"
              :title="account.transactionCount > 0 ? '有交易记录的账户不能删除' : undefined"
              @click="remove(account)"
            >
              删除
            </button>
          </div>
          <p v-if="account.transactionCount > 0" class="delete-hint">有交易记录的账户不能删除</p>
        </article>
      </div>
    </section>

    <UiModal v-model="modalOpen" :title="editingId ? '编辑账户' : '新建账户'">
      <form class="form-grid" @submit.prevent="save">
        <div class="form-field">
          <label for="account-name">账户名称</label>
          <input id="account-name" v-model.trim="form.name" class="input" required maxlength="50" />
        </div>
        <div class="form-field">
          <label for="account-type">账户类型</label>
          <select id="account-type" v-model="form.type" class="select">
            <option v-for="option in accountTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </div>
        <div class="form-field">
          <label for="account-initial-balance">期初余额</label>
          <input id="account-initial-balance" v-model="form.initialBalance" class="input" type="number" step="0.01" />
        </div>
        <div class="form-field">
          <label for="account-icon">图标</label>
          <input id="account-icon" v-model.trim="form.icon" class="input" maxlength="50" />
        </div>
        <div class="form-field full">
          <span id="account-color-label" class="field-label">颜色</span>
          <ColorSwatches v-model="form.color" labelledby="account-color-label" />
        </div>
        <div class="form-field full">
          <MessageBanner :message="modalMessage.text" :type="modalMessage.type" @dismissed="clearModalMessage" />
        </div>
        <div class="form-field full row-actions">
          <button class="button" type="submit" :disabled="saving">
            <span v-if="saving" class="t-shimmer button-shimmer" data-text="保存中...">保存中...</span>
            <template v-else>保存</template>
          </button>
          <button class="button secondary" type="button" @click="modalOpen = false">取消</button>
        </div>
      </form>
    </UiModal>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import { accountsApi } from '@/api'
import AnimatedNumber from '@/components/AnimatedNumber.vue'
import AppShell from '@/components/AppShell.vue'
import ColorSwatches from '@/components/ColorSwatches.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import PageHeader from '@/components/PageHeader.vue'
import UiModal from '@/components/UiModal.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useMessage } from '@/composables/useMessage'
import { useLedgerStore } from '@/stores/ledger'
import type { Account, AccountType } from '@/types'
import { accountTypeLabel, accountTypeOptions, defaultAccountIcon, formatMoney, toNumber } from '@/utils/format'

const ledger = useLedgerStore()
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | null>(null)
const togglingId = ref<number | null>(null)
const settingDefaultId = ref<number | null>(null)
const accounts = ref<Account[]>([])
const form = reactive({
  name: '',
  type: 'CASH' as AccountType,
  initialBalance: '0',
  icon: '',
  color: '#EB5E28'
})
const { message, showSuccess, showError, clear } = useMessage()
const { message: modalMessage, showError: showModalError, clear: clearModalMessage } = useMessage()
const { confirm } = useConfirm()

const totalBalance = computed(() => accounts.value.reduce((sum, account) => sum + toNumber(account.currentBalance), 0))
const activeCount = computed(() => accounts.value.filter(account => account.active).length)

onMounted(loadAccounts)

async function loadAccounts(): Promise<void> {
  loading.value = true
  try {
    accounts.value = await accountsApi.list()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: 'CASH',
    initialBalance: '0',
    icon: defaultAccountIcon('CASH'),
    color: '#EB5E28'
  })
  clearModalMessage()
  modalOpen.value = true
}

function openEdit(account: Account): void {
  editingId.value = account.id
  Object.assign(form, {
    name: account.name,
    type: account.type,
    initialBalance: String(account.initialBalance ?? 0),
    icon: account.icon || defaultAccountIcon(account.type),
    color: account.color || '#EB5E28'
  })
  clearModalMessage()
  modalOpen.value = true
}

async function save(): Promise<void> {
  saving.value = true
  clearModalMessage()
  try {
    if (editingId.value) {
      await accountsApi.update(editingId.value, form)
      showSuccess('账户已更新。')
    } else {
      await accountsApi.create(form)
      showSuccess('账户已创建。')
    }
    modalOpen.value = false
    await loadAccounts()
  } catch (error) {
    showModalError(error)
  } finally {
    saving.value = false
  }
}

async function toggleDefault(account: Account): Promise<void> {
  if (settingDefaultId.value !== null) return
  settingDefaultId.value = account.id
  try {
    await accountsApi.setDefault(account.default ? null : account.id)
    showSuccess(account.default ? `已取消「${account.name}」的默认设置。` : `已将「${account.name}」设为默认账户。`)
    await loadAccounts()
  } catch (error) {
    showError(error)
  } finally {
    settingDefaultId.value = null
  }
}

async function toggleActive(account: Account): Promise<void> {
  if (togglingId.value !== null) return
  togglingId.value = account.id
  try {
    await accountsApi.update(account.id, { active: !account.active })
    await loadAccounts()
  } catch (error) {
    showError(error)
  } finally {
    togglingId.value = null
  }
}

async function remove(account: Account): Promise<void> {
  if (!(await confirm({ title: '删除账户', message: `确认删除账户「${account.name}」？`, danger: true }))) return
  try {
    await accountsApi.delete(account.id)
    showSuccess('账户已删除。')
    await loadAccounts()
  } catch (error) {
    showError(error)
  }
}
</script>

<style scoped>
.account-card {
  display: grid;
  gap: var(--space-md);
}

.account-card.disabled {
  opacity: 0.62;
}

.account-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.account-head h2 {
  margin: 0;
  font-size: 18px;
}

.account-head p {
  margin: 4px 0 0;
  color: var(--color-muted);
}

.account-icon {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: var(--radius-md);
  color: white;
  font-size: 22px;
}

.default-badge {
  vertical-align: middle;
}

.account-balance {
  font-family: var(--font-num);
  font-size: 26px;
  font-weight: 700;
}

.account-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-md);
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 700;
}

.delete-hint {
  margin: calc(-1 * var(--space-sm)) 0 0;
  color: var(--color-muted);
  font-size: 12px;
}
</style>
