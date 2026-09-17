<template>
  <AppShell>
    <PageHeader title="统计分析" subtitle="查看当前账本的收支汇总、分类构成和趋势。" />

    <MessageBanner :message="message.text" :type="message.type" @dismissed="clear" />

    <RouterLink class="card ai-entry-card" :to="{ name: 'analysis' }">
      <span class="ai-entry-icon"><MessagesSquare /></span>
      <span class="ai-entry-text">
        <strong>AI 分析</strong>
        <small>有什么想问的？让 AI 帮你分析</small>
      </span>
      <ChevronRight class="ai-entry-arrow" />
    </RouterLink>

    <section class="section-stack">
      <div ref="periodTabsRef" class="pill-tabs period-tabs t-tabs" role="tablist">
        <span class="t-tabs-pill" aria-hidden="true"></span>
        <button
          v-for="item in periods"
          :key="item.value"
          class="t-tab"
          type="button"
          role="tab"
          :id="`period-tab-${item.value}`"
          aria-controls="statistics-period-panel"
          :tabindex="period === item.value ? 0 : -1"
          :aria-selected="period === item.value"
          @click="selectPeriod(item.value)"
          @keydown="handlePeriodTabKeydown"
        >
          {{ item.label }}
        </button>
      </div>

      <div id="statistics-period-panel" class="grid cols-4" role="tabpanel" :aria-labelledby="activePeriodTabId">
        <div class="card stat-card">
          <span class="stat-label">收入</span>
          <AnimatedNumber class="stat-value positive" :value="formatMoney(summary?.totalIncome)" />
          <StatDelta :pct="summary?.incomeChangePct ?? null" :invert="false" />
        </div>
        <div class="card stat-card">
          <span class="stat-label">支出</span>
          <AnimatedNumber class="stat-value negative" :value="formatMoney(summary?.totalExpense)" />
          <StatDelta :pct="summary?.expenseChangePct ?? null" :invert="true" />
        </div>
        <div class="card stat-card">
          <span class="stat-label">结余</span>
          <AnimatedNumber class="stat-value" :class="balanceClass" :value="formatMoney(summary?.balance)" />
          <StatDelta :pct="balanceChangePct" :invert="false" />
        </div>
        <div class="card stat-card">
          <span class="stat-label">交易笔数</span>
          <AnimatedNumber class="stat-value" :value="summary?.transactionCount || 0" />
        </div>
      </div>

      <div class="grid cols-2 chart-grid">
        <section class="panel">
          <div class="panel-header">
            <h2 class="panel-title">分类构成</h2>
            <div ref="categoryTabsRef" class="pill-tabs category-toggle t-tabs" role="tablist">
              <span class="t-tabs-pill" aria-hidden="true"></span>
              <button
                v-for="t in categoryTypes"
                :key="t.value"
                type="button"
                role="tab"
                class="t-tab"
                :id="`category-tab-${t.value.toLowerCase()}`"
                aria-controls="category-chart-panel"
                :tabindex="categoryType === t.value ? 0 : -1"
                :aria-selected="categoryType === t.value"
                @click="selectCategoryType(t.value)"
                @keydown="handleCategoryTabKeydown"
              >
                {{ t.label }}
              </button>
            </div>
          </div>
          <div id="category-chart-panel" role="tabpanel" :aria-labelledby="activeCategoryTabId">
            <div v-if="loading" class="loading-state chart-state">
              <span class="t-shimmer" data-text="正在翻账本...">正在翻账本...</span>
            </div>
            <template v-else-if="categoryStats.length">
              <div class="chart-box">
                <canvas ref="categoryCanvas" role="img" :aria-label="categoryChartLabel" />
              </div>
              <div class="table-wrap category-table-wrap">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>分类</th>
                      <th>金额</th>
                      <th>占比</th>
                      <th>笔数</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in categoryStats" :key="item.categoryId">
                      <td data-label="分类">{{ categoryLabel(item) }}</td>
                      <td data-label="金额" class="num">{{ formatMoney(item.amount) }}</td>
                      <td data-label="占比" class="num">{{ formatPercent(item.percentage) }}%</td>
                      <td data-label="笔数">{{ item.transactionCount }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
            <div v-else class="empty-state chart-state">
              暂无分类统计
              <RouterLink class="empty-action" :to="{ name: 'dashboard' }">去记一笔</RouterLink>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2 class="panel-title">{{ trendConfig.title }}</h2>
          </div>
          <div v-if="loading" class="loading-state chart-state">
            <span class="t-shimmer" data-text="正在翻账本...">正在翻账本...</span>
          </div>
          <div v-else-if="trendData.length" class="chart-box">
            <canvas ref="trendCanvas" role="img" :aria-label="trendChartLabel" />
          </div>
          <div v-else class="empty-state chart-state">
            暂无趋势数据
            <RouterLink class="empty-action" :to="{ name: 'dashboard' }">去记一笔</RouterLink>
          </div>
        </section>
      </div>

      <section class="panel top-expenses-section">
        <div class="panel-header">
          <h2 class="panel-title">大额支出</h2>
        </div>
        <div v-if="loading" class="loading-state">
          <span class="t-shimmer" data-text="正在翻账本...">正在翻账本...</span>
        </div>
        <div v-else-if="!topExpenses.length" class="empty-state coins">暂无支出记录</div>
        <ol v-else class="rank-list">
          <li v-for="(t, i) in topExpenses" :key="t.id" class="rank-item">
            <span class="rank-num" :class="'rank-' + (i + 1)">#{{ i + 1 }}</span>
            <div class="rank-main">
              <span class="rank-desc">{{ t.description }}</span>
              <span class="rank-sub">
                {{ categoryName(t) }}
                <template v-if="t.parsedMerchant"> · {{ t.parsedMerchant }}</template>
                · {{ formatDate(t.transactionDate) }}
              </span>
            </div>
            <span class="rank-amount">{{ formatMoney(t.amount) }}</span>
          </li>
        </ol>
      </section>
    </section>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { ChevronRight, MessagesSquare } from 'lucide-vue-next'
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  DoughnutController,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip
} from 'chart.js'
import { statisticsApi } from '@/api'
import AppShell from '@/components/AppShell.vue'
import AnimatedNumber from '@/components/AnimatedNumber.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatDelta from '@/components/StatDelta.vue'
import { useMessage } from '@/composables/useMessage'
import type { CategoryStatistic, StatisticsSummary, Transaction, TransactionType, TrendData } from '@/types'
import { baseChartOptions, currencyTooltip, seriesColors } from '@/utils/chartTheme'
import { formatDate, formatMoney, formatPercent, toNumber } from '@/utils/format'
import { useSlidingTabs } from '@/utils/slidingTabs'

Chart.register(
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  DoughnutController,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip
)

const periods = [
  { value: 'current_month', label: '本月' },
  { value: 'last_3_months', label: '近 3 月' },
  { value: 'last_6_months', label: '近 6 月' },
  { value: 'current_year', label: '今年' }
]

const categoryTypes: Array<{ value: TransactionType; label: string }> = [
  { value: 'EXPENSE', label: '支出' },
  { value: 'INCOME', label: '收入' }
]

const period = ref('current_month')
const categoryType = ref<TransactionType>('EXPENSE')
const loading = ref(false)
const summary = ref<StatisticsSummary | null>(null)
const categoryStats = ref<CategoryStatistic[]>([])
const trendData = ref<TrendData[]>([])
const topExpenses = ref<Transaction[]>([])
const categoryCanvas = ref<HTMLCanvasElement | null>(null)
const trendCanvas = ref<HTMLCanvasElement | null>(null)
// Chart 实例是重型外部对象，深响应式代理无意义且拖慢销毁/重建，用 shallowRef 存
const categoryChart = shallowRef<Chart | null>(null)
const trendChart = shallowRef<Chart | null>(null)
const { message, showError, clear } = useMessage()
const { tabsRef: periodTabsRef, syncTabs: syncPeriodTabs, handleTabKeydown: handlePeriodTabKeydown } = useSlidingTabs(index => {
  const target = periods[index]
  if (target) selectPeriod(target.value)
})
const { tabsRef: categoryTabsRef, syncTabs: syncCategoryTabs, handleTabKeydown: handleCategoryTabKeydown } = useSlidingTabs(index => {
  const target = categoryTypes[index]
  if (target) selectCategoryType(target.value)
})

// 竞态守卫：快速切换周期/类型时，仅最后一次发起的请求允许落值
let loadSeq = 0
let categorySeq = 0

const balanceClass = computed(() => (toNumber(summary.value?.balance) >= 0 ? 'positive' : 'negative'))
const activePeriodTabId = computed(() => `period-tab-${period.value}`)
const activeCategoryTabId = computed(() => `category-tab-${categoryType.value.toLowerCase()}`)
const categoryChartLabel = computed(() => {
  const typeLabel = categoryType.value === 'INCOME' ? '收入' : '支出'
  const details = categoryStats.value.map(item => (
    `${categoryLabel(item)} ${formatMoney(item.amount)}，占比 ${formatPercent(item.percentage)}%，${item.transactionCount} 笔`
  ))
  return `${typeLabel}分类构成。${details.join('；')}`
})
const trendChartLabel = computed(() => {
  const details = trendData.value.map(item => (
    `${item.month}：收入 ${formatMoney(item.income)}，支出 ${formatMoney(item.expense)}，结余 ${formatMoney(item.balance)}`
  ))
  return `${trendConfig.value.title}。${details.join('；')}`
})

const trendConfig = computed(() => {
  switch (period.value) {
    case 'current_month':
      return { daily: true, months: 0, title: '本月每日趋势' }
    case 'last_3_months':
      return { daily: false, months: 3, title: '近 3 月趋势' }
    case 'last_6_months':
      return { daily: false, months: 6, title: '近 6 月趋势' }
    case 'current_year':
      return { daily: false, months: new Date().getMonth() + 1, title: '今年趋势' }
    default:
      return { daily: false, months: 6, title: '趋势' }
  }
})

/** 结余环比：由当期结余与上一同期结余推导（结余上升为好） */
const balanceChangePct = computed<number | null>(() => {
  const cur = toNumber(summary.value?.balance)
  const prev = toNumber(summary.value?.prevTotalIncome) - toNumber(summary.value?.prevTotalExpense)
  if (prev === 0) return null
  return (cur - prev) / prev * 100
})

onMounted(loadAll)
onBeforeUnmount(() => {
  categoryChart.value?.destroy()
  trendChart.value?.destroy()
})

function selectPeriod(value: string): void {
  if (period.value === value) return
  period.value = value
  syncPeriodTabs()
  void loadAll()
}

function selectCategoryType(value: TransactionType): void {
  if (categoryType.value === value) return
  categoryType.value = value
  syncCategoryTabs()
  void loadCategory()
}

async function loadAll(): Promise<void> {
  const mySeq = ++loadSeq
  const myCategorySeq = ++categorySeq
  loading.value = true
  clear()
  try {
    const cfg = trendConfig.value
    const [summaryResult, categoryResult, trendResult, recentResult] = await Promise.all([
      statisticsApi.summary(period.value),
      statisticsApi.byCategory(period.value, categoryType.value),
      cfg.daily ? statisticsApi.dailyTrend(period.value) : statisticsApi.trend(cfg.months),
      statisticsApi.topExpenses(period.value)
    ])
    if (mySeq !== loadSeq) return  // 已有更新的请求发出，丢弃本次过期响应
    summary.value = summaryResult
    trendData.value = trendResult
    topExpenses.value = recentResult
    // 分类统计可能已被更晚发起的 loadCategory 刷新，仅在仍是最新时落值
    if (myCategorySeq === categorySeq) categoryStats.value = categoryResult
    // 先撤下加载态再等一帧，让 v-if 的 canvas 完成挂载后才渲染图表
    loading.value = false
    await nextTick()
    // 图表始终按当前 state 重绘（categoryStats 可能来自更新的 loadCategory，重绘同样正确）
    renderCategoryChart()
    renderTrendChart()
  } catch (error) {
    if (mySeq !== loadSeq) return
    showError(error, '统计数据加载失败')
  } finally {
    if (mySeq === loadSeq) loading.value = false
  }
}

async function loadCategory(): Promise<void> {
  const mySeq = ++categorySeq
  try {
    const result = await statisticsApi.byCategory(period.value, categoryType.value)
    if (mySeq !== categorySeq) return
    categoryStats.value = result
    await nextTick()
    renderCategoryChart()
  } catch (error) {
    if (mySeq !== categorySeq) return
    showError(error, '分类统计加载失败')
  }
}

function renderCategoryChart(): void {
  categoryChart.value?.destroy()
  if (!categoryCanvas.value || !categoryStats.value.length) return
  categoryChart.value = new Chart(categoryCanvas.value, {
    type: 'doughnut',
    data: {
      labels: categoryStats.value.map(categoryLabel),
      datasets: [
        {
          data: categoryStats.value.map(item => toNumber(item.amount)),
          // seriesColors 按条目数循环取色，条目 > 8 时图例与图块颜色保持一致
          backgroundColor: seriesColors(categoryStats.value.length),
          borderColor: '#FFFDF8',
          borderWidth: 2
        }
      ]
    },
    options: {
      ...baseChartOptions(),
      plugins: {
        ...baseChartOptions().plugins,
        tooltip: currencyTooltip<'doughnut'>()
      }
    }
  })
}

function renderTrendChart(): void {
  trendChart.value?.destroy()
  if (!trendCanvas.value || !trendData.value.length) return
  const cfg = trendConfig.value

  if (cfg.daily) {
    trendChart.value = new Chart(trendCanvas.value, {
      type: 'bar',
      data: {
        labels: trendData.value.map(item => formatDayLabel(item.month)),
        datasets: [
          { label: '收入', data: trendData.value.map(item => toNumber(item.income)), backgroundColor: 'rgba(47, 156, 99, 0.82)' },
          { label: '支出', data: trendData.value.map(item => toNumber(item.expense)), backgroundColor: 'rgba(235, 94, 40, 0.82)' }
        ]
      },
      options: {
        ...baseChartOptions(),
        plugins: {
          ...baseChartOptions().plugins,
          tooltip: currencyTooltip<'bar'>()
        },
        scales: { y: { beginAtZero: true } }
      }
    })
    return
  }

  trendChart.value = new Chart(trendCanvas.value, {
    type: 'line',
    data: {
      labels: trendData.value.map(item => item.month),
      datasets: [
        {
          label: '收入',
          data: trendData.value.map(item => toNumber(item.income)),
          borderColor: '#2F9C63',
          backgroundColor: 'rgba(47, 156, 99, 0.12)',
          tension: 0.35
        },
        {
          label: '支出',
          data: trendData.value.map(item => toNumber(item.expense)),
          borderColor: '#EB5E28',
          backgroundColor: 'rgba(235, 94, 40, 0.1)',
          tension: 0.35
        },
        {
          label: '结余',
          data: trendData.value.map(item => toNumber(item.balance)),
          borderColor: '#F2A33C',
          backgroundColor: 'rgba(242, 163, 60, 0.08)',
          borderDash: [5, 4],
          tension: 0.35
        }
      ]
    },
    options: {
      ...baseChartOptions(),
      plugins: {
        ...baseChartOptions().plugins,
        tooltip: currencyTooltip<'line'>()
      },
      scales: { y: { beginAtZero: true } }
    }
  })
}

function categoryLabel(item: CategoryStatistic): string {
  return item.parentCategoryName || item.categoryName || '未分类'
}

function formatDayLabel(day: string): string {
  const parts = day.split('-')
  if (parts.length < 3) return day
  return `${parseInt(parts[1], 10)}/${parseInt(parts[2], 10)}`
}

function categoryName(t: Transaction): string {
  return t.category || '未分类'
}
</script>

<style scoped>
.top-expenses-section {
  margin-top: var(--space-md);
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0 var(--space-lg) var(--space-md);
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border-soft);
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-num {
  flex: none;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  font-family: var(--font-num);
  font-size: 13px;
  font-weight: 800;
  color: var(--color-muted);
  background: var(--color-surface-soft);
}

.rank-1 {
  color: #b8860b;
  background: rgba(184, 134, 11, 0.12);
}

.rank-2 {
  color: var(--color-muted);
  background: rgba(120, 106, 81, 0.12);
}

.rank-3 {
  color: #a0522d;
  background: rgba(160, 82, 45, 0.1);
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-desc {
  display: block;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-sub {
  display: block;
  margin-top: 3px;
  color: var(--color-muted);
  font-size: 12px;
}

.rank-amount {
  flex: none;
  font-family: var(--font-num);
  font-size: 17px;
  font-weight: 700;
}

.chart-box {
  position: relative;
  min-width: 0;
  height: 320px;
  overflow: hidden;
  padding: var(--space-lg);
}

.chart-box canvas {
  max-width: 100%;
}

/* 面板内空/加载态：替代 320px 空白 chart-box 的占位 */
.chart-state {
  margin: var(--space-lg);
}

.empty-action {
  display: block;
  margin-top: 10px;
  color: var(--color-primary-text);
  font-weight: 750;
}

.empty-action:hover {
  color: var(--color-primary-hover);
}

.chart-grid {
  align-items: stretch;
  min-width: 0;
}

.chart-grid > .panel {
  min-width: 0;
}

.category-toggle {
  padding: 3px;
}

.category-toggle .t-tab {
  height: 28px;
  padding: 4px 14px;
  font-size: 13px;
}

.category-toggle .t-tabs-pill {
  top: 3px;
  height: 28px;
}

.category-table-wrap {
  overflow-x: auto;
  padding: 0 var(--space-lg) var(--space-lg);
}

.category-table-wrap .data-table {
  min-width: 0;
}

.category-table-wrap .data-table th,
.category-table-wrap .data-table td {
  padding: 10px 12px;
}

.category-table-wrap .data-table th {
  font-size: 12px;
}

@media (max-width: 820px) {
  .period-tabs {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    width: 100%;
    min-width: 0;
    gap: 3px;
  }

  .period-tabs .t-tab {
    min-width: 0;
    padding-inline: 6px;
  }

  .chart-box {
    height: 260px;
    padding: 14px;
  }

  .category-table-wrap {
    padding: 0 14px 16px;
  }
}

.ai-entry-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 16px;
  text-decoration: none;
  color: inherit;
}

.ai-entry-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  flex-shrink: 0;
}

.ai-entry-icon svg { width: 20px; height: 20px; }

.ai-entry-text { display: flex; flex-direction: column; gap: 2px; }

.ai-entry-text small { color: var(--color-muted); }

.ai-entry-arrow { margin-left: auto; width: 18px; height: 18px; color: var(--color-muted); }
</style>
