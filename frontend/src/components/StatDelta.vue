<template>
  <span v-if="pct !== undefined" class="stat-delta" :class="deltaClass">
    {{ deltaText }} <small>较上期</small>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  pct: number | null
  /** invert=true 时「上升」视为不利（如支出增加）；默认上升为有利（收入/结余增加） */
  invert?: boolean
}>()

const deltaText = computed(() => {
  if (props.pct === null || props.pct === undefined) return '新增'
  if (props.pct === 0) return '持平'
  const arrow = props.pct > 0 ? '↑' : '↓'
  return `${arrow}${Math.abs(props.pct).toFixed(1)}%`
})

const deltaClass = computed(() => {
  if (props.pct === null || props.pct === undefined || props.pct === 0) return 'delta-neutral'
  const up = props.pct > 0
  const good = props.invert ? !up : up
  return good ? 'delta-up' : 'delta-down'
})
</script>

<style scoped>
.stat-delta {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  font-family: var(--font-num);
}

.stat-delta small {
  color: var(--color-muted);
  font-weight: 500;
  font-family: var(--font-sans);
}

.delta-up {
  color: var(--color-success-text);
}

.delta-down {
  color: var(--color-danger-text);
}

.delta-neutral {
  color: var(--color-muted);
}
</style>
