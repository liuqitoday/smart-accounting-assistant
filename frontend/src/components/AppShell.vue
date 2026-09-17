<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/dashboard">
        <span class="brand-icon"><Wallet /></span>
        <span>记账助手</span>
      </RouterLink>

      <nav class="nav-list">
        <RouterLink v-for="item in navItems" :key="item.to" class="nav-link" :to="item.to">
          <component :is="item.icon" class="nav-icon" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <nav class="nav-list nav-list-secondary desktop-only" aria-label="管理">
        <span class="nav-section-label">管理</span>
        <RouterLink v-for="item in managementItems" :key="item.to" class="nav-link nav-link-secondary" :to="item.to">
          <component :is="item.icon" class="nav-icon" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <p class="sidebar-foot">每一笔,都记得 ✨</p>
    </aside>

    <main class="main-panel">
      <header class="topbar">
        <RouterLink class="topbar-brand mobile-only" to="/dashboard">
          <span class="brand-icon"><Wallet /></span>
          <span>记账助手</span>
        </RouterLink>

        <div class="topbar-context">
          <span v-if="ledger.state.activeLedger" class="ledger-chip">
            <span class="ledger-dot" :style="{ background: ledger.state.activeLedger.color || '#EB5E28' }" />
            {{ ledger.state.activeLedger.icon || '📒' }} {{ ledger.state.activeLedger.name }}
            <small>{{ roleLabel(ledger.state.activeLedger.myRole) }}</small>
          </span>
          <span v-else class="ledger-chip muted">尚未创建账本</span>
        </div>

        <div class="topbar-actions desktop-only">
          <LedgerSelector v-if="ledger.state.ledgers.length" />
          <div ref="accountMenuRef" class="account-menu-wrap">
            <button
              ref="accountTriggerRef"
              class="account-trigger"
              type="button"
              aria-haspopup="menu"
              :aria-controls="accountMenuId"
              :aria-expanded="accountMenuOpen ? 'true' : 'false'"
              @click="toggleAccountMenu"
              @keydown.down.prevent="openAccountMenu('first')"
              @keydown.up.prevent="openAccountMenu('last')"
            >
              <UserRound />
              <span class="username">{{ auth.state.username }}</span>
              <ChevronDown class="account-chevron" :class="{ open: accountMenuOpen }" />
            </button>

            <div
              v-if="accountMenuMounted"
              :id="accountMenuId"
              ref="accountDropdownRef"
              class="account-dropdown t-dropdown"
              :class="{ 'is-open': accountMenuOpen, 'is-closing': accountMenuClosing }"
              data-origin="top-right"
              role="menu"
              @keydown="handleMenuKeydown"
            >
              <button class="account-menu-item" type="button" role="menuitem" @click="openPasswordModal">
                <KeyRound />
                <span>修改密码</span>
              </button>
              <button class="account-menu-item danger" type="button" role="menuitem" @click="logout">
                <LogOut />
                <span>退出登录</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      <section class="content">
        <slot />
      </section>

      <nav class="tabbar">
        <RouterLink class="tab-item" to="/dashboard">
          <Home />
          <span>首页</span>
        </RouterLink>
        <RouterLink class="tab-item" to="/transactions">
          <ListChecks />
          <span>明细</span>
        </RouterLink>
        <span class="tab-record-wrap">
          <button class="tab-record" type="button" aria-label="记一笔" @click="goRecord">
            <Plus />
          </button>
        </span>
        <RouterLink class="tab-item" to="/statistics">
          <BarChart3 />
          <span>统计</span>
        </RouterLink>
        <RouterLink class="tab-item" to="/profile">
          <UserRound />
          <span>我的</span>
        </RouterLink>
      </nav>
    </main>

    <ChangePasswordModal v-model="passwordModalOpen" @changed="handlePasswordChanged" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChart3,
  BookOpen,
  ChevronDown,
  Home,
  KeyRound,
  Landmark,
  ListChecks,
  LogOut,
  MessagesSquare,
  Plus,
  Sparkles,
  Tags,
  UserRound,
  Wallet
} from 'lucide-vue-next'
import ChangePasswordModal from '@/components/ChangePasswordModal.vue'
import LedgerSelector from '@/components/LedgerSelector.vue'
import { useAuthStore } from '@/stores/auth'
import { useLedgerStore } from '@/stores/ledger'
import { cssMs } from '@/utils/css'
import { roleLabel } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()
const ledger = useLedgerStore()
const passwordModalOpen = ref(false)
const accountMenuRef = ref<HTMLElement | null>(null)
const accountTriggerRef = ref<HTMLButtonElement | null>(null)
const accountDropdownRef = ref<HTMLElement | null>(null)
const accountMenuMounted = ref(false)
const accountMenuOpen = ref(false)
const accountMenuClosing = ref(false)
let accountCloseTimer: number | undefined
let accountOpenFrame: number | undefined
let menuFocusTarget: 'first' | 'last' | undefined
const accountMenuId = `account-menu-${useId().replace(/:/g, '')}`

const navItems = [
  { to: '/dashboard', label: '智能记账', icon: Sparkles },
  { to: '/transactions', label: '账单明细', icon: ListChecks },
  { to: '/statistics', label: '统计分析', icon: BarChart3 },
  { to: '/analysis', label: 'AI 分析', icon: MessagesSquare },
  { to: '/accounts', label: '账户资产', icon: Landmark }
]

const managementItems = [
  { to: '/ledgers', label: '账本设置', icon: BookOpen },
  { to: '/tags', label: '标签管理', icon: Tags }
]

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  clearAccountMenuTimers()
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleKeydown)
})

async function goRecord(): Promise<void> {
  await router.push({ name: 'dashboard', query: { focus: '1' } })
}

async function logout(): Promise<void> {
  closeAccountMenu()
  await auth.logout()
  ledger.reset()
  await router.replace({ name: 'login' })
}

function toggleAccountMenu(): void {
  if (accountMenuOpen.value) {
    closeAccountMenu()
    return
  }
  openAccountMenu()
}

function openAccountMenu(focusTarget?: 'first' | 'last'): void {
  if (accountMenuOpen.value) {
    if (focusTarget) focusMenuEdge(focusTarget)
    return
  }
  clearAccountMenuTimers()
  menuFocusTarget = focusTarget
  accountMenuMounted.value = true
  accountMenuClosing.value = false
  accountMenuOpen.value = false

  void nextTick(() => {
    accountOpenFrame = window.requestAnimationFrame(() => {
      accountMenuOpen.value = true
      if (menuFocusTarget) {
        focusMenuEdge(menuFocusTarget)
        menuFocusTarget = undefined
      }
    })
  })
}

function closeAccountMenu(restoreFocus = false): void {
  clearAccountMenuTimers()
  menuFocusTarget = undefined
  if (!accountMenuMounted.value) {
    if (restoreFocus) accountTriggerRef.value?.focus({ preventScroll: true })
    return
  }

  accountMenuOpen.value = false
  accountMenuClosing.value = true
  accountCloseTimer = window.setTimeout(() => {
    accountMenuMounted.value = false
    accountMenuClosing.value = false
  }, cssMs('--dropdown-close-dur', 150))
  if (restoreFocus) accountTriggerRef.value?.focus({ preventScroll: true })
}

function openPasswordModal(): void {
  closeAccountMenu(true)
  passwordModalOpen.value = true
}

async function handlePasswordChanged(): Promise<void> {
  await auth.logout()
  ledger.reset()
  await router.replace({ name: 'login' })
}

function handleDocumentClick(event: MouseEvent): void {
  if (!accountMenuMounted.value) return
  const target = event.target
  if (target instanceof Node && accountMenuRef.value?.contains(target)) return
  closeAccountMenu()
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && accountMenuMounted.value) {
    closeAccountMenu(true)
  }
}

function handleMenuKeydown(event: KeyboardEvent): void {
  const items = menuItems()
  if (!items.length) return
  const currentIndex = Math.max(0, items.indexOf(document.activeElement as HTMLButtonElement))

  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    closeAccountMenu(true)
    return
  }
  if (event.key === 'Tab') {
    closeAccountMenu()
    return
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return

  event.preventDefault()
  let nextIndex = currentIndex
  if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = items.length - 1
  else if (event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % items.length
  else nextIndex = (currentIndex - 1 + items.length) % items.length
  items[nextIndex]?.focus({ preventScroll: true })
}

function menuItems(): HTMLButtonElement[] {
  return Array.from(accountDropdownRef.value?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]:not([disabled])') ?? [])
}

function focusMenuEdge(edge: 'first' | 'last'): void {
  const items = menuItems()
  const target = edge === 'first' ? items[0] : items[items.length - 1]
  target?.focus({ preventScroll: true })
}

function clearAccountMenuTimers(): void {
  if (accountOpenFrame !== undefined) {
    window.cancelAnimationFrame(accountOpenFrame)
    accountOpenFrame = undefined
  }
  if (accountCloseTimer !== undefined) {
    window.clearTimeout(accountCloseTimer)
    accountCloseTimer = undefined
  }
}
</script>
