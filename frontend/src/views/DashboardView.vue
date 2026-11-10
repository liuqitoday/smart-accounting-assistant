<template>
  <div class="dashboard-page">
    <PageHeader title="智能记账" :subtitle="ledger.state.activeLedger?.name || '记下每一笔日常'" />
    <section class="card month-overview" aria-label="本月收支">
      <div class="overview-head"><span>{{ monthLabel }}支出</span><RouterLink :to="{ name: 'statistics' }">查看统计 <ChevronRight aria-hidden="true" /></RouterLink></div>
      <template v-if="summary">
        <p class="overview-amount num">{{ formatMoney(summary.totalExpense) }}</p>
        <div class="overview-detail"><span>收入 <strong class="num">{{ formatMoney(summary.totalIncome) }}</strong></span><span>结余 <strong class="num">{{ formatMoney(summary.balance) }}</strong></span><span>{{ summary.transactionCount }} 笔</span></div>
        <p v-if="summaryError" class="muted-text" role="status">更新失败，正在显示上次的数据。<button class="button ghost" type="button" @click="loadSummary">重试</button></p>
      </template>
      <div v-else-if="summaryError" class="overview-unavailable" role="status"><p>本月汇总暂时无法加载</p><button class="button secondary" type="button" @click="loadSummary">重试</button></div>
      <div v-else aria-label="正在加载本月汇总" role="status"><SkeletonBlock class="summary-skeleton" /><SkeletonBlock class="summary-detail-skeleton" /></div>
    </section>
    <section class="quick-record" aria-label="开始记账">
      <button class="record-primary" type="button" :disabled="!ledger.canEdit.value" @click="openRecording('text')"><Sparkles aria-hidden="true" /><span><strong>说一句，记一笔</strong><small>金额、日期和分类，帮你填好</small></span><ChevronRight aria-hidden="true" /></button>
      <div class="quick-secondary"><button class="button secondary" type="button" :disabled="!ledger.canEdit.value" @click="openRecording('manual')"><PencilLine aria-hidden="true" />手动记账</button><button v-if="capabilities?.imageParsingEnabled" class="button secondary" type="button" :disabled="!ledger.canEdit.value" @click="openRecording('image')"><ImagePlus aria-hidden="true" />截图记账</button></div>
      <p v-if="!ledger.canEdit.value" class="muted-text">当前账本为只读，仍可查看账单和统计。</p>
    </section>
    <section class="panel recent-panel">
      <div class="panel-header"><h2 class="panel-title">最近记下</h2><RouterLink :to="{ name: 'transactions' }">全部账单 <ChevronRight aria-hidden="true" /></RouterLink></div>
      <div v-if="recent === null && !recentError" class="recent-loading" role="status" aria-label="正在加载最近账目"><SkeletonBlock v-for="i in 3" :key="i" /></div>
      <div v-else-if="recentError && recent === null" class="empty-state">最近账目加载失败 <button class="button ghost" type="button" @click="loadRecent">重试</button></div>
      <div v-else-if="!recent?.length" class="empty-state">还没有账目，从第一笔开始吧。</div>
      <ul v-else class="recent-list"><li v-for="item in recent" :key="item.id"><span class="recent-main"><strong>{{ item.description }}</strong><small>{{ formatDate(item.transactionDate) }} · {{ item.category || '转账' }}</small></span><span class="num" :class="item.type === 'INCOME' ? 'positive' : item.type === 'EXPENSE' ? 'negative' : ''">{{ item.type === 'INCOME' ? '+' : item.type === 'EXPENSE' ? '−' : '' }}{{ formatMoney(item.amount) }}</span></li></ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronRight, ImagePlus, PencilLine, Sparkles } from 'lucide-vue-next'
import { statisticsApi, transactionsApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import { useDataRefresh } from '@/composables/useDataRefresh'
import { useLedgerStore } from '@/stores/ledger'
import { openRecording } from '@/stores/recording'
import { formatDate, formatMoney } from '@/utils/format'
import type { StatisticsSummary, Transaction, TransactionCapabilities } from '@/types'

const ledger = useLedgerStore()
const route = useRoute()
const summary = ref<StatisticsSummary | null>(null)
const summaryError = ref(false)
const recent = ref<Transaction[] | null>(null)
const recentError = ref(false)
const capabilities = ref<TransactionCapabilities | null>(null)
const monthLabel = computed(() => `${new Date().getMonth() + 1} 月`)
useDataRefresh(async () => { await Promise.all([loadSummary(), loadRecent(), loadCapabilities()]) })
watch(() => route.query.focus, value => { if (value) openRecording('text') }, { immediate: true })
async function loadSummary(): Promise<void> {
  try { summary.value = await statisticsApi.summary('current_month'); summaryError.value = false }
  catch { summaryError.value = true }
}
async function loadRecent(): Promise<void> {
  try { recent.value = await statisticsApi.recent(); recentError.value = false }
  catch { recentError.value = true }
}
async function loadCapabilities(): Promise<void> {
  try { capabilities.value = await transactionsApi.capabilities() } catch { /* Keep the last known capability. */ }
}
</script>

<style scoped>
.dashboard-page { display: grid; gap: 22px; max-width: 960px; margin: 0 auto; }
.dashboard-page :deep(.page-header) { margin-bottom: 0; }
.month-overview { padding: 24px; }
.overview-head, .overview-detail { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.overview-head { color: var(--color-muted); }
.overview-head a, .panel-header a { display: inline-flex; align-items: center; gap: 3px; color: var(--color-primary-text); font-size: 13px; }
.overview-head svg, .panel-header a svg { width: 15px; height: 15px; }
.overview-amount { font-size: clamp(36px, 7vw, 52px); line-height: 1.1; margin: 17px 0 22px; letter-spacing: -1px; }
.overview-detail { flex-wrap: wrap; justify-content: flex-start; color: var(--color-muted); font-size: 13px; }
.overview-detail strong { margin-left: 5px; color: var(--color-text); font-size: 17px; font-weight: 500; }
.overview-detail > span:last-child { margin-left: auto; }
.summary-skeleton { width: 60%; height: 52px; margin: 18px 0; }
.summary-detail-skeleton { width: 90%; height: 20px; }
.overview-unavailable { padding: 14px 0; }
.quick-record { display: grid; gap: 10px; }
.record-primary { display: flex; align-items: center; gap: 14px; width: 100%; padding: 21px 22px; text-align: left; border: 1px solid var(--color-primary); border-radius: var(--radius-md); background: var(--color-primary); color: white; }
.record-primary > svg:first-child { width: 28px; height: 28px; }
.record-primary > svg:last-child { margin-left: auto; width: 22px; }
.record-primary span { display: grid; gap: 5px; }
.record-primary strong { font-size: 19px; }
.record-primary small { font-size: 12px; opacity: .9; }
.quick-secondary { display: flex; gap: 10px; }
.quick-secondary > button { flex: 1; min-height: 48px; }
.recent-list { list-style: none; margin: 0; padding: 0; }
.recent-list li { display: flex; align-items: center; gap: 16px; padding: 17px var(--space-lg); border-bottom: 1px solid var(--color-border-soft); }
.recent-list li:last-child { border-bottom: 0; }
.recent-main { min-width: 0; flex: 1; display: grid; gap: 5px; }
.recent-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px; font-weight: 500; }
.recent-main small { color: var(--color-muted); font-size: 12px; }
.recent-list li > .num { font-size: 19px; white-space: nowrap; }
.recent-loading { display: grid; gap: 22px; padding: 18px var(--space-lg); }
.recent-loading > div { height: 35px; }
@media (max-width: 820px) {
  .dashboard-page { gap: 18px; }
  .month-overview { padding: 20px; }
  .recent-panel .panel-header { align-items: center; flex-direction: row; }
  .recent-list li, .recent-loading { padding-inline: 16px; }
}
</style>
