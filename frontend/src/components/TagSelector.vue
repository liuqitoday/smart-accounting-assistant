<template>
  <div class="tag-selector" role="group" :aria-labelledby="labelledby">
    <button
      v-for="tag in tags"
      :key="tag.id"
      type="button"
      class="tag-chip selectable"
      :class="{ active: selectedIds.includes(tag.id) }"
      :aria-pressed="selectedIds.includes(tag.id)"
      @click="toggle(tag.id)"
    >
      <span class="tag-dot" :style="{ background: tag.color || '#95876F' }" />
      {{ tag.name }}
    </button>
    <span v-if="!tags.length" class="muted-text">暂无标签</span>
  </div>
</template>

<script setup lang="ts">
import type { Tag } from '@/types'

const props = defineProps<{
  tags: Tag[]
  selectedIds: number[]
  labelledby?: string
}>()

const emit = defineEmits<{
  'update:selectedIds': [value: number[]]
}>()

function toggle(id: number): void {
  if (props.selectedIds.includes(id)) {
    emit(
      'update:selectedIds',
      props.selectedIds.filter(item => item !== id)
    )
    return
  }
  emit('update:selectedIds', [...props.selectedIds, id])
}
</script>
