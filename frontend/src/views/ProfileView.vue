<template>
  <AppShell>
    <PageHeader title="我的" subtitle="管理账户、标签和账本,切换当前记账空间。" />

    <section class="section-stack">
      <div class="card profile-card">
        <span class="avatar">{{ avatarChar }}</span>
        <div class="profile-info">
          <h2>{{ auth.state.username }}</h2>
          <p v-if="ledger.state.activeLedger">
            当前账本:{{ ledger.state.activeLedger.icon || '📒' }} {{ ledger.state.activeLedger.name }}
            <span class="badge neutral">{{ roleLabel(ledger.state.activeLedger.myRole) }}</span>
          </p>
          <p v-else class="muted-text">还没有账本,先创建一个吧</p>
        </div>
      </div>

      <div v-if="ledger.state.ledgers.length > 1" class="card switch-card">
        <span class="switch-label">切换账本</span>
        <LedgerSelector />
      </div>

      <nav class="panel menu-list">
        <RouterLink v-for="item in menuItems" :key="item.to" class="menu-item" :to="item.to">
          <span class="menu-icon" :style="{ background: item.bg }">
            <component :is="item.icon" />
          </span>
          <span class="menu-text">
            <strong>{{ item.label }}</strong>
            <small>{{ item.hint }}</small>
          </span>
          <ChevronRight class="menu-chevron" />
        </RouterLink>
        <button class="menu-item menu-button" type="button" @click="passwordModalOpen = true">
          <span class="menu-icon security-icon">
            <KeyRound />
          </span>
          <span class="menu-text">
            <strong>修改密码</strong>
            <small>更新账户登录密码</small>
          </span>
          <ChevronRight class="menu-chevron" />
        </button>
      </nav>

      <button class="button secondary logout-button" type="button" @click="logout">退出登录</button>
    </section>

    <ChangePasswordModal v-model="passwordModalOpen" @changed="handlePasswordChanged" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, ChevronRight, KeyRound, Landmark, Tags } from 'lucide-vue-next'
import AppShell from '@/components/AppShell.vue'
import ChangePasswordModal from '@/components/ChangePasswordModal.vue'
import LedgerSelector from '@/components/LedgerSelector.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useLedgerStore } from '@/stores/ledger'
import { roleLabel } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()
const ledger = useLedgerStore()
const passwordModalOpen = ref(false)

const avatarChar = computed(() => (auth.state.username || '友').slice(0, 1).toUpperCase())

const menuItems = [
  { to: '/accounts', label: '我的账户', hint: '现金、银行卡与网络支付', icon: Landmark, bg: '#FDEAD7' },
  { to: '/tags', label: '标签', hint: '给交易贴上自己的标记', icon: Tags, bg: '#E1F0DD' },
  { to: '/ledgers', label: '账本', hint: '多账本与成员共享', icon: BookOpen, bg: '#DCEDF7' }
]

async function logout(): Promise<void> {
  await auth.logout()
  ledger.reset()
  await router.replace({ name: 'login' })
}

async function handlePasswordChanged(): Promise<void> {
  await auth.logout()
  ledger.reset()
  await router.replace({ name: 'login' })
}
</script>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: linear-gradient(140deg, #f4793f, var(--color-primary) 60%, var(--color-primary-dark));
  color: #fff6ec;
  font-size: 24px;
  font-weight: 800;
  box-shadow: var(--shadow-soft);
}

.profile-info h2 {
  margin: 0;
  font-size: 20px;
}

.profile-info p {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 7px 0 0;
  color: var(--color-muted);
  font-size: 14px;
}

.switch-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 16px var(--space-lg);
}

.switch-label {
  font-weight: 750;
}

.menu-list {
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 15px 18px;
  transition: background 0.15s ease;
}

.menu-item + .menu-item {
  border-top: 1px solid var(--color-border-soft);
}

.menu-item:hover {
  background: rgba(235, 94, 40, 0.04);
}

.menu-button {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--color-text);
  text-align: left;
}

.menu-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  color: #8a6a3a;
}

.security-icon {
  background: #f6dfdf;
  color: #a94740;
}

.menu-icon svg {
  width: 19px;
  height: 19px;
}

.menu-text {
  display: grid;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.menu-text small {
  color: var(--color-muted);
}

.menu-chevron {
  width: 18px;
  height: 18px;
  color: var(--color-muted);
}

.logout-button {
  width: 100%;
  color: var(--color-danger-text);
}
</style>
