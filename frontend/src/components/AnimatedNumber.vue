<template>
  <span :key="displayValue" class="t-digit-group is-animating" :aria-label="displayValue">
    <span
      v-for="(char, index) in chars"
      :key="`${displayValue}-${index}`"
      class="t-digit"
      :data-stagger="staggerFor(index)"
      aria-hidden="true"
    >
      {{ char }}
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value: string | number
}>()

const displayValue = computed(() => String(props.value))
const chars = computed(() => displayValue.value.split(''))

function staggerFor(index: number): string | undefined {
  if (index === chars.value.length - 2) return '1'
  if (index === chars.value.length - 1) return '2'
  return undefined
}
</script>
