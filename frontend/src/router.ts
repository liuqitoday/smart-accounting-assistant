import { nextTick } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useLedgerStore } from '@/stores/ledger'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
    { path: '/dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '智能记账' } },
    { path: '/transactions', name: 'transactions', component: () => import('@/views/TransactionsView.vue'), meta: { title: '账单明细' } },
    { path: '/statistics', name: 'statistics', component: () => import('@/views/StatisticsView.vue'), meta: { title: '统计分析' } },
    { path: '/analysis', name: 'analysis', component: () => import('@/views/AnalysisView.vue'), meta: { title: 'AI 分析' } },
    { path: '/accounts', name: 'accounts', component: () => import('@/views/AccountsView.vue'), meta: { title: '账户管理' } },
    { path: '/tags', name: 'tags', component: () => import('@/views/TagsView.vue'), meta: { title: '标签管理' } },
    { path: '/ledgers', name: 'ledgers', component: () => import('@/views/LedgersView.vue'), meta: { title: '账本管理' } },
    { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人中心' } },
    { path: '/index.html', redirect: '/dashboard' },
    { path: '/login.html', redirect: '/login' },
    { path: '/transactions.html', redirect: '/transactions' },
    { path: '/statistics.html', redirect: '/statistics' },
    { path: '/accounts.html', redirect: '/accounts' },
    { path: '/tags.html', redirect: '/tags' },
    { path: '/ledgers.html', redirect: '/ledgers' },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  await auth.initialize()

  if (!to.meta.public && !auth.isAuthenticated.value) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isAuthenticated.value) {
    return { name: 'dashboard' }
  }
  if (!to.meta.public && auth.isAuthenticated.value) {
    const ledger = useLedgerStore()
    if (!ledger.state.initialized) {
      await ledger.load()
    }
    if (!ledger.state.ledgers.length && to.name !== 'ledgers') {
      return { name: 'ledgers' }
    }
  }
  return true
})

router.afterEach(async to => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '记账助手'
  document.title = title === '记账助手' ? title : `${title} · 记账助手`
  if (to.query.focus) return

  await nextTick()
  window.requestAnimationFrame(() => {
    const heading = document.querySelector<HTMLElement>('main h1')
    if (!heading) return
    const hadTabindex = heading.hasAttribute('tabindex')
    if (!hadTabindex) heading.setAttribute('tabindex', '-1')
    heading.focus({ preventScroll: true })
    if (!hadTabindex) {
      heading.addEventListener('blur', () => heading.removeAttribute('tabindex'), { once: true })
    }
  })
})

export default router
