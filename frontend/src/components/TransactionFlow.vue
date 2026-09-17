<template>
  <div class="flow-list">
    <section v-for="group in groups" :key="group.date" class="flow-group">
      <header class="flow-day">
        <span>{{ group.label }}</span>
        <span class="flow-day-sums">
          <span v-if="group.expense > 0">支 {{ formatPlainMoney(group.expense) }}</span>
          <span v-if="group.income > 0" class="positive">收 {{ formatPlainMoney(group.income) }}</span>
        </span>
      </header>

      <article v-for="item in group.items" :key="item.id" class="flow-item">
        <span class="cat-icon" :style="{ background: visual(item).bg }">{{ visual(item).icon }}</span>
        <div class="flow-main">
          <div class="flow-title">{{ item.description }}</div>
          <div class="flow-sub">
            <span>{{ categoryText(item) }}</span>
            <span v-if="item.parsedMerchant">· {{ item.parsedMerchant }}</span>
            <span v-if="item.relatedUser">· 相关人员 {{ item.relatedUser }}</span>
            <span v-for="tag in item.tags || []" :key="tag.id" class="flow-mini-tag">
              <span class="tag-dot" :style="{ background: tag.color || '#95876F' }" />
              {{ tag.name }}
            </span>
          </div>
        </div>
        <div class="flow-side">
          <span class="flow-amount" :class="{ income: item.type === 'INCOME', transfer: item.type === 'TRANSFER' }">
            <template v-if="item.type === 'TRANSFER'">⇄ {{ formatPlainMoney(item.amount) }}</template>
            <template v-else>{{ item.type === 'INCOME' ? '+' : '-' }}{{ formatPlainMoney(item.amount) }}</template>
          </span>
          <div v-if="showActions" class="flow-actions">
            <button v-if="item.type !== 'TRANSFER'" class="icon-button" type="button" aria-label="编辑" :disabled="!canEdit" @click="$emit('edit', item)">
              <Pencil />
            </button>
            <button class="icon-button" type="button" aria-label="删除" :disabled="!canEdit" @click="$emit('remove', item)">
              <Trash2 />
            </button>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Pencil, Trash2 } from 'lucide-vue-next'
import type { Transaction } from '@/types'
import { categoryVisual, type CategoryVisual } from '@/utils/categoryIcon'
import { formatPlainMoney, toNumber } from '@/utils/format'

interface DayGroup {
  date: string
  label: string
  income: number
  expense: number
  items: Transaction[]
}

const props = withDefaults(
  defineProps<{
    transactions: Transaction[]
    showActions?: boolean
    canEdit?: boolean
  }>(),
  {
    showActions: false,
    canEdit: true
  }
)

defineEmits<{
  edit: [transaction: Transaction]
  remove: [transaction: Transaction]
}>()

const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const groups = computed<DayGroup[]>(() => {
  const result: DayGroup[] = []
  const byDate = new Map<string, DayGroup>()
  for (const item of props.transactions) {
    const date = (item.transactionDate || '').slice(0, 10)
    let group = byDate.get(date)
    if (!group) {
      group = { date, label: dayLabel(date), income: 0, expense: 0, items: [] }
      byDate.set(date, group)
      result.push(group)
    }
    group.items.push(item)
    if (item.type === 'INCOME') {
      group.income += toNumber(item.amount)
    } else if (item.type === 'EXPENSE') {
      group.expense += toNumber(item.amount)
    }
    // TRANSFER 不计入当日收支合计
  }
  return result
})

function dayLabel(date: string): string {
  if (!date) return '未知日期'
  const target = new Date(`${date}T00:00:00`)
  if (Number.isNaN(target.getTime())) return date

  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const diffDays = Math.round((startOfToday - target.getTime()) / 86400000)

  // 非当前年显示年份（跨年场景清晰标识）
  const yearPrefix = target.getFullYear() !== now.getFullYear()
    ? `${target.getFullYear()}年 `
    : ''

  const base = `${yearPrefix}${target.getMonth() + 1}月${target.getDate()}日 ${weekdays[target.getDay()]}`

  if (diffDays === 0) return `今天 · ${base}`
  if (diffDays === 1) return `昨天 · ${base}`
  return base
}

function visual(item: Transaction): CategoryVisual {
  if (item.type === 'TRANSFER') {
    return { icon: '⇄', bg: '#ECE5DA', color: '#95876F' }
  }
  return categoryVisual(item.category, item.parentCategoryName, item.type)
}

function categoryText(item: Transaction): string {
  if (item.type === 'TRANSFER') return '转账'
  return item.parentCategoryName ? `${item.parentCategoryName} / ${item.category}` : item.category || '未分类'
}
</script>

<style scoped>
.flow-amount.transfer {
  color: var(--color-muted, #95876f);
}
</style>
