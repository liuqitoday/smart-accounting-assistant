<template>
  <div class="color-swatches" role="group" :aria-labelledby="labelledby">
    <button
      v-for="color in colors"
      :key="color"
      class="color-swatch"
      :class="{ active: modelValue === color }"
      type="button"
      :style="{ background: color }"
      :aria-label="`选择颜色 ${color}`"
      :aria-pressed="modelValue === color"
      @click="$emit('update:modelValue', color)"
    />
  </div>
</template>

<script setup lang="ts">
import { presetColors } from '@/utils/format'

withDefaults(
  defineProps<{
    modelValue: string
    colors?: string[]
    labelledby?: string
  }>(),
  {
    colors: () => presetColors
  }
)

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>
