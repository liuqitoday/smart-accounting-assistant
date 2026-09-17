<template>
  <AppShell>
    <PageHeader title="账本管理" subtitle="创建多个账本并管理成员权限。">
      <template #actions>
        <button class="button" type="button" @click="openCreate">
          <Plus />
          新建账本
        </button>
      </template>
    </PageHeader>

    <MessageBanner :message="message.text" :type="message.type" @dismissed="clear" />

    <div v-if="ledgerStore.state.loading" class="loading-state">
      <span class="t-shimmer" data-text="加载中...">加载中...</span>
    </div>
    <div v-else-if="!ledgerStore.state.ledgers.length" class="empty-state books">还没有账本,创建第一本开始记账吧</div>
    <div v-else class="grid cols-3">
      <article v-for="item in ledgerStore.state.ledgers" :key="item.id" class="card ledger-card">
        <div class="ledger-card-head">
          <div class="ledger-avatar" :style="{ background: item.color || '#EB5E28' }">{{ item.icon || '📒' }}</div>
          <div>
            <h2>{{ item.name }}</h2>
            <p>{{ item.description || '暂无描述' }}</p>
          </div>
        </div>
        <div class="ledger-meta">
          <span class="badge neutral">{{ roleLabel(item.myRole) }}</span>
          <span>{{ item.memberCount }} 名成员</span>
          <span v-if="item.default" class="positive">默认账本</span>
        </div>
        <div class="row-actions ledger-actions">
          <button class="button secondary compact" type="button" :disabled="activeId === item.id" @click="switchLedger(item)">
            {{ activeId === item.id ? '当前账本' : '设为当前' }}
          </button>
          <button
            class="button secondary compact"
            type="button"
            :disabled="item.default || savingDefaultId === item.id"
            @click="setDefaultLedger(item)"
          >
            <span v-if="savingDefaultId === item.id" class="t-shimmer button-shimmer" data-text="设置中...">设置中...</span>
            <template v-else>{{ item.default ? '已默认' : '设为默认' }}</template>
          </button>
          <button class="button secondary compact" type="button" @click="openMembers(item)">成员</button>
          <!-- 标签按管理页按当前账本隔离，跳转前会静默切换账本（re-key 整页刷新，无法先行提示），用 title 说明副作用 -->
          <button
            class="button secondary compact"
            type="button"
            title="切换到此账本并管理标签"
            @click="goTags(item)"
          >
            标签
          </button>
          <button class="button secondary compact" type="button" :disabled="!item.owner" @click="openEdit(item)">编辑</button>
          <button class="button danger compact" type="button" :disabled="!item.owner" @click="removeLedger(item)">删除</button>
        </div>
      </article>
    </div>

    <UiModal v-model="ledgerModalOpen" :title="editingLedgerId ? '编辑账本' : '新建账本'">
      <form class="form-grid" @submit.prevent="saveLedger">
        <div class="form-field">
          <label for="ledger-name">账本名称</label>
          <input id="ledger-name" v-model.trim="ledgerForm.name" class="input" required maxlength="100" />
        </div>
        <div class="form-field">
          <label for="ledger-icon">图标</label>
          <input id="ledger-icon" v-model.trim="ledgerForm.icon" class="input" maxlength="50" />
        </div>
        <div class="form-field full">
          <label for="ledger-description">描述</label>
          <textarea id="ledger-description" v-model.trim="ledgerForm.description" class="textarea" maxlength="500" />
        </div>
        <div class="form-field full">
          <span id="ledger-color-label" class="field-label">颜色</span>
          <ColorSwatches v-model="ledgerForm.color" labelledby="ledger-color-label" />
        </div>
        <!-- 弹窗内错误内嵌展示（页面级横幅会被遮罩挡住），模式同 ChangePasswordModal -->
        <div class="form-field full">
          <MessageBanner :message="modalMessage.text" :type="modalMessage.type" @dismissed="clearModalMessage" />
        </div>
        <div class="form-field full row-actions">
          <button class="button" type="submit" :disabled="savingLedger">
            <span v-if="savingLedger" class="t-shimmer button-shimmer" data-text="保存中...">保存中...</span>
            <template v-else>保存</template>
          </button>
          <button class="button secondary" type="button" @click="ledgerModalOpen = false">取消</button>
        </div>
      </form>
    </UiModal>

    <UiModal v-model="membersModalOpen" :title="membersTitle" size="lg">
      <div v-if="selectedLedger" class="section-stack">
        <MessageBanner :message="modalMessage.text" :type="modalMessage.type" @dismissed="clearModalMessage" />
        <form v-if="selectedLedger.owner" class="member-invite" @submit.prevent="invite">
          <input v-model.trim="inviteForm.username" class="input" placeholder="用户名" aria-label="成员用户名" required />
          <select v-model="inviteForm.role" class="select" aria-label="成员角色">
            <option value="EDITOR">可编辑</option>
            <option value="VIEWER">仅查看</option>
          </select>
          <button class="button" type="submit" :disabled="savingMember">添加成员</button>
        </form>

        <div v-if="membersLoading" class="loading-state">
          <span class="t-shimmer" data-text="加载中...">加载中...</span>
        </div>
        <div v-else-if="!members.length" class="empty-state">暂无成员</div>
        <div v-else class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>成员</th>
                <th>角色</th>
                <th>加入时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in members" :key="member.username">
                <td data-label="成员">{{ member.username }}</td>
                <td data-label="角色">
                  <select
                    class="select compact-select"
                    :aria-label="`调整 ${member.username} 的角色`"
                    :value="member.role"
                    :disabled="!selectedLedger.owner || member.role === 'OWNER'"
                    @change="changeRole(member.username, ($event.target as HTMLSelectElement).value as LedgerRole)"
                  >
                    <option v-if="member.role === 'OWNER'" value="OWNER">所有者</option>
                    <option value="EDITOR">可编辑</option>
                    <option value="VIEWER">仅查看</option>
                  </select>
                </td>
                <td data-label="加入时间">{{ formatDate(member.joinedAt) }}</td>
                <td data-label="操作">
                  <div class="row-actions">
                    <button
                      class="button secondary compact"
                      type="button"
                      :disabled="!selectedLedger.owner || member.role === 'OWNER'"
                      @click="transfer(member.username)"
                    >
                      转让
                    </button>
                    <button
                      class="button danger compact"
                      type="button"
                      :disabled="!selectedLedger.owner || member.role === 'OWNER'"
                      @click="removeMember(member.username)"
                    >
                      移除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <button
          v-if="!selectedLedger.owner"
          class="button danger"
          type="button"
          @click="leaveLedger"
        >
          退出该账本
        </button>
      </div>
    </UiModal>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from 'lucide-vue-next'
import { ledgerApi } from '@/api'
import AppShell from '@/components/AppShell.vue'
import ColorSwatches from '@/components/ColorSwatches.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import PageHeader from '@/components/PageHeader.vue'
import UiModal from '@/components/UiModal.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useMessage } from '@/composables/useMessage'
import { useLedgerStore } from '@/stores/ledger'
import type { Ledger, LedgerMember, LedgerRole } from '@/types'
import { formatDate, roleLabel } from '@/utils/format'

const router = useRouter()
const ledgerStore = useLedgerStore()
const { confirm } = useConfirm()
const ledgerModalOpen = ref(false)
const membersModalOpen = ref(false)
const savingLedger = ref(false)
const savingMember = ref(false)
const savingDefaultId = ref<number | null>(null)
const membersLoading = ref(false)
const editingLedgerId = ref<number | null>(null)
const selectedLedger = ref<Ledger | null>(null)
const members = ref<LedgerMember[]>([])
const ledgerForm = reactive({
  name: '',
  description: '',
  icon: '📒',
  color: '#EB5E28'
})
const inviteForm = reactive({
  username: '',
  role: 'EDITOR' as LedgerRole
})
// 页面级横幅 + 弹窗内横幅各一份（弹窗打开时页面横幅在遮罩后面，弹窗内操作的反馈走 modalMessage）
const { message, showSuccess, showError, clear } = useMessage()
const {
  message: modalMessage,
  showError: showModalError,
  clear: clearModalMessage
} = useMessage()

const activeId = computed(() => ledgerStore.state.activeLedger?.id)
const membersTitle = computed(() => (selectedLedger.value ? `${selectedLedger.value.name} · 成员管理` : '成员管理'))

function openCreate(): void {
  editingLedgerId.value = null
  Object.assign(ledgerForm, {
    name: '',
    description: '',
    icon: '📒',
    color: '#EB5E28'
  })
  clearModalMessage()
  ledgerModalOpen.value = true
}

function openEdit(ledger: Ledger): void {
  editingLedgerId.value = ledger.id
  Object.assign(ledgerForm, {
    name: ledger.name,
    description: ledger.description || '',
    icon: ledger.icon || '📒',
    color: ledger.color || '#EB5E28'
  })
  clearModalMessage()
  ledgerModalOpen.value = true
}

async function saveLedger(): Promise<void> {
  savingLedger.value = true
  try {
    if (editingLedgerId.value) {
      await ledgerApi.update(editingLedgerId.value, ledgerForm)
      showSuccess('账本已更新。')
    } else {
      await ledgerApi.create(ledgerForm)
      showSuccess('账本已创建。')
    }
    ledgerModalOpen.value = false
    await ledgerStore.load()
  } catch (error) {
    showModalError(error)
  } finally {
    savingLedger.value = false
  }
}

async function removeLedger(ledger: Ledger): Promise<void> {
  const confirmed = await confirm({
    title: '删除账本',
    message: `将永久删除「${ledger.name}」及其中全部交易、账户、标签等数据，删除后无法恢复。确定要删除吗？`,
    confirmText: '永久删除',
    danger: true
  })
  if (!confirmed) return
  try {
    await ledgerApi.delete(ledger.id)
    showSuccess('账本已删除。')
    await ledgerStore.load()
  } catch (error) {
    showError(error)
  }
}

async function switchLedger(ledger: Ledger): Promise<void> {
  try {
    await ledgerStore.switchTo(ledger.id)
    // 切换成功会触发 App 级 re-key 整页刷新（本组件随即卸载），无需也无法展示成功提示
  } catch (error) {
    showError(error)
  }
}

async function setDefaultLedger(ledger: Ledger): Promise<void> {
  savingDefaultId.value = ledger.id
  try {
    await ledgerStore.setDefault(ledger.id)
    showSuccess(`已将「${ledger.name}」设为默认账本。`)
  } catch (error) {
    showError(error)
  } finally {
    savingDefaultId.value = null
  }
}

async function goTags(ledger: Ledger): Promise<void> {
  await ledgerStore.switchTo(ledger.id)
  await router.push({ name: 'tags' })
}

async function openMembers(ledger: Ledger): Promise<void> {
  selectedLedger.value = ledger
  membersModalOpen.value = true
  inviteForm.username = ''
  inviteForm.role = 'EDITOR'
  clearModalMessage()
  await loadMembers()
}

async function loadMembers(): Promise<void> {
  if (!selectedLedger.value) return
  membersLoading.value = true
  try {
    members.value = await ledgerApi.members(selectedLedger.value.id)
  } catch (error) {
    showModalError(error)
  } finally {
    membersLoading.value = false
  }
}

async function invite(): Promise<void> {
  if (!selectedLedger.value) return
  savingMember.value = true
  try {
    await ledgerApi.inviteMember(selectedLedger.value.id, inviteForm.username, inviteForm.role)
    inviteForm.username = ''
    await Promise.all([loadMembers(), ledgerStore.load()])
  } catch (error) {
    showModalError(error)
  } finally {
    savingMember.value = false
  }
}

async function changeRole(username: string, role: LedgerRole): Promise<void> {
  if (!selectedLedger.value) return
  try {
    await ledgerApi.updateMemberRole(selectedLedger.value.id, username, role)
    await loadMembers()
  } catch (error) {
    showModalError(error)
    // 失败回滚：select 已停留在用户选的新值上，回读成员列表恢复真实角色显示
    await loadMembers()
  }
}

async function removeMember(username: string): Promise<void> {
  if (!selectedLedger.value) return
  const confirmed = await confirm({
    title: '移除成员',
    message: `确认将成员「${username}」从账本中移除？移除后其将立即失去该账本的访问权限。`,
    confirmText: '移除',
    danger: true
  })
  if (!confirmed) return
  try {
    await ledgerApi.removeMember(selectedLedger.value.id, username)
    await Promise.all([loadMembers(), ledgerStore.load()])
  } catch (error) {
    showModalError(error)
  }
}

async function transfer(username: string): Promise<void> {
  if (!selectedLedger.value) return
  const confirmed = await confirm({
    title: '转让所有权',
    message: `确认将账本所有权转让给「${username}」？转让后你将不再是该账本的所有者。`,
    confirmText: '确认转让'
  })
  if (!confirmed) return
  try {
    await ledgerApi.transfer(selectedLedger.value.id, username)
    await Promise.all([loadMembers(), ledgerStore.load()])
  } catch (error) {
    showModalError(error)
  }
}

async function leaveLedger(): Promise<void> {
  if (!selectedLedger.value) return
  const confirmed = await confirm({
    title: '退出账本',
    message: `确认退出账本「${selectedLedger.value.name}」？退出后将无法再访问其中的数据。`,
    confirmText: '退出账本',
    danger: true
  })
  if (!confirmed) return
  try {
    await ledgerApi.leave(selectedLedger.value.id)
    membersModalOpen.value = false
    await ledgerStore.load()
  } catch (error) {
    showModalError(error)
  }
}
</script>

<style scoped>
.ledger-card {
  display: grid;
  gap: var(--space-md);
}

.ledger-card-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ledger-card-head h2 {
  margin: 0;
  font-size: 18px;
}

.ledger-card-head p {
  margin: 4px 0 0;
  color: var(--color-muted);
}

.ledger-avatar {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: var(--radius-md);
  color: white;
  font-size: 24px;
}

.ledger-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 700;
}

.ledger-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.ledger-actions .button {
  white-space: nowrap;
}

.member-invite {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px auto;
  gap: 10px;
}

.compact-select {
  min-width: 130px;
  padding: 7px 9px;
}

@media (max-width: 820px) {
  .ledger-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .member-invite {
    grid-template-columns: 1fr;
  }
}
</style>
