<template>
  <div class="result-card">
    <p v-if="card.title" class="result-title">{{ card.title }}</p>

    <template v-if="v1">
      <!-- AGGREGATE: 大数字 -->
      <div v-if="v1.queryType === 'AGGREGATE'" class="big-number">
        {{ v1AggregateText }}
      </div>

      <!-- TOP_N / LIST: 排行/明细列表 -->
      <ul v-else-if="v1.queryType === 'TOP_N' || v1.queryType === 'LIST'" class="rank-list">
        <li v-if="!v1.rows.length" class="empty-row">该时间段没有符合条件的数据</li>
        <li v-for="(row, i) in v1.rows" :key="i" class="rank-row">
          <span v-if="v1.queryType === 'TOP_N'" class="rank-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <span class="rank-main">
            <span class="rank-desc">{{ String(row[2] ?? '') }}</span>
            <span class="rank-meta">{{ rowMeta(row) }}</span>
          </span>
          <span class="rank-amount">{{ formatMoney(toNumber(row[1] as number | string)) }}</span>
        </li>
      </ul>

      <!-- TREND: 折线图 -->
      <div v-else-if="v1.queryType === 'TREND'" class="chart-wrap">
        <canvas ref="chartCanvas" role="img" :aria-label="chartAriaLabel" />
      </div>

      <!-- GROUP_BY: 环形图 + 图例 -->
      <div v-else-if="v1.queryType === 'GROUP_BY'">
        <div v-if="v1.rows.length" class="chart-wrap donut">
          <canvas ref="chartCanvas" role="img" :aria-label="chartAriaLabel" />
        </div>
        <ul v-if="v1.rows.length" class="legend-list">
          <li v-for="(row, i) in v1.rows" :key="i">
            <span class="legend-dot" :style="{ background: seriesColor(i) }" />
            <span class="legend-label">{{ String(row[0] ?? '未知') }}</span>
            <span class="legend-value">{{ formatMoney(toNumber(row[1] as number | string)) }}</span>
          </li>
        </ul>
        <p v-if="!v1.rows.length" class="empty-row">该时间段没有符合条件的数据</p>
      </div>
    </template>

    <template v-else-if="v2">
      <p v-if="periodSummary" class="result-meta">{{ periodSummary }}</p>
      <p v-if="filterSummary" class="result-meta">{{ filterSummary }}</p>
      <p v-if="v2.kind === 'BREAKDOWN' && v2.associationShare" class="share-note">标签关联占比</p>

      <div v-if="v2.kind === 'AGGREGATE'" class="big-number">
        {{ formatAnalysisValue(v2.values.value, v2.unit) }}
      </div>

      <dl v-else-if="v2.kind === 'PERIOD_COMPARE'" class="compare-grid">
        <div>
          <dt>本期</dt>
          <dd>{{ formatAnalysisValue(v2.values.current, v2.unit) }}</dd>
        </div>
        <div>
          <dt>上期</dt>
          <dd>{{ formatAnalysisValue(v2.values.previous, v2.unit) }}</dd>
        </div>
        <div>
          <dt>差额</dt>
          <dd>{{ formatAnalysisValue(v2.values.difference, v2.unit) }}</dd>
        </div>
        <div>
          <dt>变化率</dt>
          <dd>{{ v2.values.changeRate == null ? '—' : formatAnalysisValue(v2.values.changeRate, 'PERCENT') }}</dd>
        </div>
      </dl>

      <dl v-else-if="v2.kind === 'NET_CASH_FLOW'" class="compare-grid">
        <div>
          <dt>收入</dt>
          <dd>{{ formatAnalysisValue(v2.values.income, v2.unit) }}</dd>
        </div>
        <div>
          <dt>支出</dt>
          <dd>{{ formatAnalysisValue(v2.values.expense, v2.unit) }}</dd>
        </div>
        <div>
          <dt>净额</dt>
          <dd>{{ formatAnalysisValue(v2.values.net, v2.unit) }}</dd>
        </div>
      </dl>

      <div v-else-if="v2.kind === 'BREAKDOWN'">
        <div v-if="v2.rows.length" class="chart-wrap donut">
          <canvas ref="chartCanvas" role="img" :aria-label="chartAriaLabel" />
        </div>
        <ul v-if="v2.rows.length" class="legend-list">
          <li v-for="(row, i) in v2.rows" :key="i">
            <span class="legend-dot" :style="{ background: seriesColor(i) }" />
            <span class="legend-label">{{ row.label || '未知' }}</span>
            <span class="legend-value">{{ formatAnalysisValue(row.value, v2.unit) }}</span>
          </li>
        </ul>
        <p v-if="!v2.rows.length" class="empty-row">该时间段没有符合条件的数据</p>
      </div>

      <div v-else-if="v2.kind === 'TREND'" class="chart-wrap">
        <canvas ref="chartCanvas" role="img" :aria-label="chartAriaLabel" />
      </div>

      <div v-else-if="v2.kind === 'AVERAGE_BY_PERIOD'">
        <div class="big-number">{{ formatAnalysisValue(v2.values.average, v2.unit) }}</div>
        <div v-if="v2.points.length" class="chart-wrap">
          <canvas ref="chartCanvas" role="img" :aria-label="chartAriaLabel" />
        </div>
      </div>

      <ul v-else-if="v2.kind === 'TRANSACTIONS'" class="rank-list">
        <li v-if="!v2.transactions.length" class="empty-row">
          {{ v2.values.detailsOmitted ? '结果过大，已省略交易明细' : '该时间段没有符合条件的数据' }}
        </li>
        <li
          v-for="tx in v2.transactions"
          :key="tx.id"
          class="rank-row transaction-row"
          role="button"
          tabindex="0"
          @click="openTransactions"
          @keydown.enter.prevent="openTransactions"
        >
          <span class="rank-main">
            <span class="rank-desc">{{ tx.description }}</span>
            <span class="rank-meta">{{ transactionMeta(tx) }}</span>
          </span>
          <span class="rank-amount">{{ formatAnalysisValue(tx.amount, v2.unit) }}</span>
        </li>
      </ul>

      <p v-else-if="v2.kind === 'UNKNOWN'" class="empty-row">
        {{ unknownWarning }}
      </p>

      <button
        v-if="canOpenTransactions"
        type="button"
        class="details-link"
        @click="openTransactions"
      >
        查看明细
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import {
  ArcElement,
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
import type {
  AnalysisQueryResult,
  AnalysisResultV2,
  AnalysisTransactionsResult
} from '@/types'
import { seriesColor, seriesColors } from '@/utils/chartTheme'
import { formatMoney, toNumber } from '@/utils/format'
import { formatAnalysisValue } from '@/utils/analysisPayload'
import { filtersFromAnalysisResult, type TransactionDeepLinkFilters } from '@/utils/transactionQuery'

Chart.register(
  ArcElement,
  CategoryScale,
  DoughnutController,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip
)

const props = defineProps<{ card: AnalysisQueryResult | AnalysisResultV2 }>()
const emit = defineEmits<{
  'open-transactions': [filters: TransactionDeepLinkFilters]
}>()

const chartCanvas = ref<HTMLCanvasElement | null>(null)
const chart = shallowRef<Chart | null>(null)

const v1 = computed(() => ('queryType' in props.card ? props.card : null))
const v2 = computed(() => ('kind' in props.card ? props.card : null))

const v1AggregateText = computed(() => {
  const card = v1.value
  if (!card) return ''
  const value = toNumber(card.rows[0]?.[0] as number | string | undefined)
  return card.metric === 'COUNT' ? `${value} 笔` : formatMoney(value)
})

const unknownWarning = computed(() => {
  const card = v2.value
  if (!card || card.kind !== 'UNKNOWN') return ''
  return card.warnings[0]?.message || '无法展示该结果类型'
})

const canOpenTransactions = computed(() => v2.value != null && v2.value.kind !== 'UNKNOWN')

const periodSummary = computed(() => {
  const card = v2.value
  if (!card?.period?.current?.start) return ''
  const current = `${card.period.current.start} ~ ${card.period.current.end}`
  if (card.period.previous?.start) {
    return `${current}（对比 ${card.period.previous.start} ~ ${card.period.previous.end}）`
  }
  return current
})

const filterSummary = computed(() => {
  const card = v2.value
  if (!card) return ''
  const parts: string[] = []
  if (card.transactionType === 'EXPENSE') parts.push('支出')
  if (card.transactionType === 'INCOME') parts.push('收入')
  const filters = card.filters ?? {}
  if (filters.merchant) parts.push(`商家 ${String(filters.merchant)}`)
  if (filters.keyword) parts.push(`关键词 ${String(filters.keyword)}`)
  if (filters.accountId) parts.push(`账户 #${String(filters.accountId)}`)
  if (filters.categoryId) parts.push(`分类 #${String(filters.categoryId)}`)
  if (Array.isArray(filters.tagIds) && filters.tagIds.length) parts.push(`标签 ${filters.tagIds.join(',')}`)
  if (filters.minAmount != null && filters.minAmount !== '') parts.push(`≥ ${String(filters.minAmount)}`)
  if (filters.maxAmount != null && filters.maxAmount !== '') parts.push(`≤ ${String(filters.maxAmount)}`)
  return parts.join(' · ')
})

const chartAriaLabel = computed(() => {
  const legacy = v1.value
  if (legacy?.queryType === 'TREND') {
    const points = legacy.rows.map(row => (
      `${String(row[0])}：收入 ${formatMoney(toNumber(row[1] as number | string))}，支出 ${formatMoney(toNumber(row[2] as number | string))}`
    ))
    return `${legacy.title || '收支趋势'}。${points.join('；')}`
  }
  if (legacy?.queryType === 'GROUP_BY') {
    const groups = legacy.rows.map(row => (
      `${String(row[0] ?? '未知')} ${formatMoney(toNumber(row[1] as number | string))}`
    ))
    return `${legacy.title || '分类构成'}。${groups.join('；')}`
  }

  const named = v2.value
  if (named?.kind === 'TREND' || named?.kind === 'AVERAGE_BY_PERIOD') {
    const points = named.points.map(point => (
      `${point.period}：收入 ${formatAnalysisValue(point.income, named.unit)}，支出 ${formatAnalysisValue(point.expense, named.unit)}`
    ))
    return `${named.title || '收支趋势'}。${points.join('；')}`
  }
  if (named?.kind === 'BREAKDOWN') {
    const groups = named.rows.map(row => (
      `${row.label || '未知'} ${formatAnalysisValue(row.value, named.unit)}`
    ))
    return `${named.title || '分类构成'}。${groups.join('；')}`
  }
  return props.card.title || '分析结果'
})

function rowMeta(row: unknown[]): string {
  const date = String(row[3] ?? '')
  const category = String(row[5] ?? row[4] ?? '未分类')
  const merchant = row[6] ? ` · ${String(row[6])}` : ''
  return `${date} · ${category}${merchant}`
}

function transactionMeta(tx: AnalysisTransactionsResult['transactions'][number]): string {
  const category = tx.parentCategory || tx.category || '未分类'
  const merchant = tx.merchant ? ` · ${tx.merchant}` : ''
  return `${tx.date} · ${category}${merchant}`
}

function openTransactions(): void {
  const card = v2.value
  if (!card) return
  emit('open-transactions', filtersFromAnalysisResult(card))
}

onMounted(renderChart)
onBeforeUnmount(() => chart.value?.destroy())

function renderChart(): void {
  chart.value?.destroy()
  if (!chartCanvas.value) return

  const legacy = v1.value
  if (legacy?.queryType === 'TREND') {
    if (!legacy.rows.length) return
    chart.value = new Chart(chartCanvas.value, {
      type: 'line',
      data: {
        labels: legacy.rows.map(row => String(row[0])),
        datasets: [
          {
            label: '收入',
            data: legacy.rows.map(row => toNumber(row[1] as number | string)),
            borderColor: '#2F9C63',
            backgroundColor: 'rgba(47, 156, 99, 0.12)',
            tension: 0.35
          },
          {
            label: '支出',
            data: legacy.rows.map(row => toNumber(row[2] as number | string)),
            borderColor: '#EB5E28',
            backgroundColor: 'rgba(235, 94, 40, 0.1)',
            tension: 0.35
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
        scales: { y: { beginAtZero: true } }
      }
    })
    return
  }

  if (legacy?.queryType === 'GROUP_BY') {
    if (!legacy.rows.length) return
    chart.value = new Chart(chartCanvas.value, {
      type: 'doughnut',
      data: {
        labels: legacy.rows.map(row => String(row[0] ?? '未知')),
        datasets: [
          {
            data: legacy.rows.map(row => toNumber(row[1] as number | string)),
            backgroundColor: seriesColors(legacy.rows.length),
            borderColor: '#FFFDF8',
            borderWidth: 2
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } }
      }
    })
    return
  }

  const named = v2.value
  if (named?.kind === 'TREND' || named?.kind === 'AVERAGE_BY_PERIOD') {
    if (!named.points.length) return
    chart.value = new Chart(chartCanvas.value, {
      type: 'line',
      data: {
        labels: named.points.map(point => point.period),
        datasets: [
          {
            label: '收入',
            data: named.points.map(point => toNumber(point.income)),
            borderColor: '#2F9C63',
            backgroundColor: 'rgba(47, 156, 99, 0.12)',
            tension: 0.35
          },
          {
            label: '支出',
            data: named.points.map(point => toNumber(point.expense)),
            borderColor: '#EB5E28',
            backgroundColor: 'rgba(235, 94, 40, 0.1)',
            tension: 0.35
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
        scales: { y: { beginAtZero: true } }
      }
    })
    return
  }

  if (named?.kind === 'BREAKDOWN') {
    if (!named.rows.length) return
    chart.value = new Chart(chartCanvas.value, {
      type: 'doughnut',
      data: {
        labels: named.rows.map(row => row.label || '未知'),
        datasets: [
          {
            data: named.rows.map(row => toNumber(row.value)),
            backgroundColor: seriesColors(named.rows.length),
            borderColor: '#FFFDF8',
            borderWidth: 2
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } }
      }
    })
  }
}
</script>

<style scoped>
.result-card {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-sm);
  background: var(--color-surface-soft);
  color: var(--color-text);
}

.result-title {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--color-muted);
}

.result-meta {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--color-muted);
}

.big-number {
  font-family: var(--font-num);
  font-size: 24px;
  font-weight: 700;
}

.share-note {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--color-muted);
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  margin: 0;
}

.compare-grid dt {
  font-size: 12px;
  color: var(--color-muted);
}

.compare-grid dd {
  margin: 2px 0 0;
  font-weight: 600;
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rank-row { display: flex; align-items: center; gap: 10px; }

.rank-no {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: var(--color-border-soft);
  color: var(--color-muted);
  font-size: 12px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.rank-no.top {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  font-weight: 600;
}

.rank-main { display: flex; flex-direction: column; min-width: 0; }

.rank-desc {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-meta { font-size: 12px; color: var(--color-muted); }

.rank-amount { margin-left: auto; font-weight: 600; flex-shrink: 0; }

.chart-wrap { position: relative; height: 200px; }

.legend-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.legend-list li { display: flex; align-items: center; gap: 8px; font-size: 13px; }

.legend-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }

.legend-label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.legend-value { margin-left: auto; color: var(--color-muted); }

.empty-row { color: var(--color-muted); font-size: 13px; }

.transaction-row { cursor: pointer; }

.details-link {
  margin-top: 10px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-primary-text);
  font-size: 13px;
  cursor: pointer;
}
</style>
