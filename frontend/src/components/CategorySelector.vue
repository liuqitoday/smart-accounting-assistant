<template>
  <div ref="selectorRef" class="category-selector" :class="{ 'has-selection': allowEmpty && selectedCategoryId }">
    <button
      :id="inputId || triggerId"
      ref="triggerRef"
      class="cs-trigger"
      type="button"
      :disabled="disabled"
      :aria-expanded="isOpen"
      aria-haspopup="listbox"
      :aria-controls="dropdownId"
      @click="toggleOpen($event)"
      @keydown.down.prevent="openDropdown(0)"
      @keydown.up.prevent="openDropdown(navValues.length - 1)"
      @keydown.esc.prevent="closeDropdown(true)"
    >
      <span class="cs-value">{{ displayText }}</span>
      <svg class="cs-chevron" :class="{ open: isOpen }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="6 9 12 15 18 9"></polyline>
      </svg>
    </button>

    <button
      v-if="allowEmpty && selectedCategoryId"
      class="cs-clear"
      type="button"
      aria-label="清除分类"
      title="清除分类"
      @click.stop="clearSelection"
    >
      <X />
    </button>

    <div
      v-if="dropdownMounted"
      :id="dropdownId"
      class="cs-dropdown t-dropdown"
      :class="{ 'is-open': isOpen, 'is-closing': isClosing }"
      data-origin="top-left"
      :aria-hidden="!isOpen"
    >
      <div class="cs-search">
        <input
          ref="searchInput"
          v-model="searchQuery"
          type="text"
          class="cs-search-input"
          :placeholder="searchPlaceholder"
          role="combobox"
          aria-label="搜索分类"
          aria-autocomplete="list"
          :aria-controls="listboxId"
          :aria-expanded="isOpen"
          :aria-activedescendant="activeDescendant"
          @keydown.esc.prevent="closeDropdown(true)"
          @keydown.enter.prevent="confirmActive"
          @keydown.down.prevent="moveHighlight(1)"
          @keydown.up.prevent="moveHighlight(-1)"
        />
      </div>

      <div :id="listboxId" ref="optionsRef" class="cs-options" role="listbox" aria-label="分类选项">
        <div
          v-if="allowEmpty"
          :id="optionId(0)"
          class="cs-option"
          role="option"
          :class="{ selected: !selectedCategoryId, highlighted: highlightIndex === 0 }"
          :aria-selected="!selectedCategoryId"
          @click="selectCategory('')"
        >
          {{ emptyLabel }}
        </div>

        <template v-for="(cat, index) in filteredCategories" :key="cat.id">
          <div
            :id="optionId(optionIndex(index))"
            class="cs-option"
            role="option"
            :class="{
              selected: selectedCategoryId === cat.id,
              highlighted: highlightIndex === optionIndex(index),
              'is-parent': !cat.parentName,
              'is-child': cat.parentName
            }"
            :aria-selected="selectedCategoryId === cat.id"
            @click="selectCategory(cat.id)"
          >
            <span v-if="cat.parentName" class="cs-indent">└</span>
            <span class="cs-label">{{ cat.name }}</span>
            <span v-if="cat.parentName" class="cs-parent-hint">{{ cat.parentName }}</span>
          </div>
        </template>

        <div v-if="filteredCategories.length === 0" class="cs-empty">
          没有找到匹配的分类
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount, useId } from 'vue'
import { X } from 'lucide-vue-next'
import { cssMs } from '@/utils/css'

interface FlatCategory {
  id: number
  name: string
  parentName?: string | null
}

const props = withDefaults(
  defineProps<{
    categories: FlatCategory[]
    modelValue: number | string | null | undefined
    allowEmpty?: boolean
    emptyLabel?: string
    placeholder?: string
    searchPlaceholder?: string
    disabled?: boolean
    inputId?: string
  }>(),
  {
    allowEmpty: true
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: number | string]
}>()

const selectorRef = ref<HTMLElement>()
const triggerRef = ref<HTMLButtonElement>()
const searchInput = ref<HTMLInputElement>()
const optionsRef = ref<HTMLElement>()
const isOpen = ref(false)
const dropdownMounted = ref(false)
const isClosing = ref(false)
const searchQuery = ref('')
/** 键盘导航高亮下标（-1 = 无高亮，此时 Enter 走「选首个匹配」的既有行为） */
const highlightIndex = ref(-1)
const instanceId = useId().replace(/:/g, '')
const triggerId = `category-trigger-${instanceId}`
const dropdownId = `category-dropdown-${instanceId}`
const listboxId = `category-listbox-${instanceId}`
let closeTimer: number | undefined
let openFrame: number | undefined

const allowEmpty = computed(() => props.allowEmpty ?? true)
const emptyLabel = computed(() => props.emptyLabel ?? '全部分类')
const placeholder = computed(() => props.placeholder ?? emptyLabel.value)
const searchPlaceholder = computed(() => props.searchPlaceholder ?? '搜索分类...')
const disabled = computed(() => props.disabled ?? false)
const selectedCategoryId = computed(() => {
  if (props.modelValue === '' || props.modelValue === null || props.modelValue === undefined) return null
  return Number(props.modelValue)
})

const displayText = computed(() => {
  if (!selectedCategoryId.value) return placeholder.value
  const cat = props.categories.find(c => c.id === selectedCategoryId.value)
  if (!cat) return placeholder.value
  return cat.parentName ? `${cat.parentName} / ${cat.name}` : cat.name
})

const filteredCategories = computed(() => {
  if (!searchQuery.value.trim()) {
    return props.categories
  }

  const query = searchQuery.value.toLowerCase()
  return props.categories.filter(cat => {
    const nameMatch = cat.name.toLowerCase().includes(query)
    const parentMatch = cat.parentName?.toLowerCase().includes(query)
    return nameMatch || parentMatch
  })
})

/** 键盘可达的选项值序列：allowEmpty 时首位是「空值」选项，其后为筛选结果 */
const navValues = computed<Array<number | ''>>(() => {
  const values: Array<number | ''> = allowEmpty.value ? [''] : []
  for (const cat of filteredCategories.value) {
    values.push(cat.id)
  }
  return values
})
const activeDescendant = computed(() => (
  highlightIndex.value >= 0 && highlightIndex.value < navValues.value.length
    ? optionId(highlightIndex.value)
    : undefined
))

function optionId(index: number): string {
  return `category-option-${instanceId}-${index}`
}

/** 第 index 个分类选项在键盘导航序列中的下标（空值选项占据 0 位时整体后移一位） */
function optionIndex(index: number): number {
  return allowEmpty.value ? index + 1 : index
}

function moveHighlight(delta: number): void {
  const count = navValues.value.length
  if (!count) return
  highlightIndex.value = highlightIndex.value < 0
    ? (delta > 0 ? 0 : count - 1)
    : (highlightIndex.value + delta + count) % count
  void nextTick(() => {
    optionsRef.value?.querySelector('.cs-option.highlighted')?.scrollIntoView({ block: 'nearest' })
  })
}

/** Enter：有键盘高亮则选高亮项；否则保留既有「选首个匹配」行为 */
function confirmActive(): void {
  const index = highlightIndex.value
  if (index >= 0 && index < navValues.value.length) {
    selectCategory(navValues.value[index])
    return
  }
  selectFirstMatch()
}

function toggleOpen(event?: MouseEvent): void {
  if (disabled.value) return
  if (isOpen.value) {
    closeDropdown()
    return
  }
  const focusSearch = !window.matchMedia('(max-width: 820px)').matches || event?.detail === 0
  openDropdown(-1, focusSearch)
}

function openDropdown(initialHighlight = -1, focusSearch = true): void {
  if (disabled.value) return
  clearMotionTimers()
  dropdownMounted.value = true
  isClosing.value = false
  isOpen.value = false
  highlightIndex.value = initialHighlight

  void nextTick(() => {
    openFrame = window.requestAnimationFrame(() => {
      isOpen.value = true
      if (focusSearch) {
        searchInput.value?.focus({ preventScroll: true })
      }
      if (initialHighlight >= 0) {
        optionsRef.value?.querySelector('.cs-option.highlighted')?.scrollIntoView({ block: 'nearest' })
      }
    })
  })
}

function closeDropdown(restoreTrigger = false): void {
  clearMotionTimers()
  if (!dropdownMounted.value) {
    if (restoreTrigger) triggerRef.value?.focus({ preventScroll: true })
    return
  }
  isOpen.value = false
  isClosing.value = true
  searchQuery.value = ''
  highlightIndex.value = -1
  closeTimer = window.setTimeout(() => {
    dropdownMounted.value = false
    isClosing.value = false
  }, cssMs('--dropdown-close-dur', 150))
  if (restoreTrigger) {
    void nextTick(() => triggerRef.value?.focus({ preventScroll: true }))
  }
}

function selectCategory(id: number | string): void {
  emit('update:modelValue', id)
  closeDropdown(true)
}

function clearSelection(): void {
  emit('update:modelValue', '')
  closeDropdown(true)
}

function selectFirstMatch() {
  if (filteredCategories.value.length > 0) {
    selectCategory(filteredCategories.value[0].id)
  } else if (allowEmpty.value) {
    selectCategory('')
  }
}

function handleClickOutside(event: MouseEvent) {
  if (selectorRef.value && !selectorRef.value.contains(event.target as Node)) {
    closeDropdown()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  clearMotionTimers()
  document.removeEventListener('click', handleClickOutside)
})

watch(isOpen, (open) => {
  if (!open) {
    searchQuery.value = ''
    highlightIndex.value = -1
  }
})

// 搜索词变化后筛选结果重排，旧高亮下标失效：回到无高亮态（Enter 即选首个匹配）
watch(searchQuery, () => {
  highlightIndex.value = -1
})

watch(disabled, value => {
  if (value) closeDropdown()
})

function clearMotionTimers(): void {
  if (openFrame !== undefined) {
    window.cancelAnimationFrame(openFrame)
    openFrame = undefined
  }
  if (closeTimer !== undefined) {
    window.clearTimeout(closeTimer)
    closeTimer = undefined
  }
}
</script>

<style scoped>
.category-selector {
  position: relative;
  width: 100%;
}

.cs-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  min-height: 42px;
  padding: 11px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #fffefb;
  color: var(--color-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.cs-trigger:hover {
  border-color: var(--color-primary);
}

.cs-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.cs-trigger:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(235, 94, 40, 0.16);
}

.cs-value {
  flex: 1;
  font-size: 0.9375rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.category-selector.has-selection .cs-value {
  padding-right: 32px;
}

.cs-clear {
  position: absolute;
  top: 5px;
  right: 34px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-muted);
}

.cs-clear:hover {
  background: var(--color-surface-soft);
  color: var(--color-text);
}

.cs-clear:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.cs-clear svg {
  width: 15px;
  height: 15px;
}

.cs-chevron {
  flex-shrink: 0;
  color: var(--color-muted);
  transition: transform 0.2s ease;
}

.cs-chevron.open {
  transform: rotate(180deg);
}

.cs-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  background: #fffefb;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-card);
  z-index: 100;
  max-height: 24rem;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* PC端让下拉稍宽一点，获得更多显示空间 */
  min-width: min(320px, 100vw - 2rem);
}

.cs-search {
  flex: none;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border-soft);
}

.cs-search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-soft);
  color: var(--color-text);
  font-size: 0.875rem;
  outline: none;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.cs-search-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(235, 94, 40, 0.16);
  background: #fffefb;
}

.cs-search-input::placeholder {
  /* 与全局 .input::placeholder 同档（styles.css），保证浅底上的可读性 */
  color: #9d8c69;
}

.cs-options {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  touch-action: pan-y;
  -webkit-overflow-scrolling: touch;
  max-height: 18rem;
  padding: 6px;
}

.cs-option {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 9px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.12s ease;
  font-size: 0.9375rem;
  color: var(--color-text);
  line-height: 1.3;
}

.cs-option:hover,
.cs-option.highlighted {
  background: var(--color-surface-soft);
}

.cs-option.selected {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  font-weight: 600;
}

@media (max-width: 820px) {
  .cs-dropdown {
    max-height: min(24rem, 55dvh);
  }

  .cs-search-input {
    font-size: 16px;
  }

  .cs-clear {
    top: 1px;
    right: 34px;
    width: 40px;
    height: 40px;
  }
}

/* 一级类目：加粗、稍大、带左侧色条标识 */
.cs-option.is-parent {
  font-weight: 600;
  font-size: 0.9375rem;
  margin-top: 4px;
  position: relative;
  padding-left: 16px;
}

.cs-option.is-parent:first-of-type {
  margin-top: 0;
}

.cs-option.is-parent::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--color-accent);
}

/* 二级类目：缩进、字号略小、带树形连接符 */
.cs-option.is-child {
  padding-left: 28px;
  font-size: 0.875rem;
  color: var(--color-text);
}

.cs-indent {
  color: var(--color-muted);
  font-weight: 300;
  margin-right: 2px;
  flex-shrink: 0;
}

.cs-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cs-parent-hint {
  font-size: 0.8125rem;
  color: var(--color-muted);
  font-weight: 400;
  white-space: nowrap;
  flex-shrink: 0;
  /* ≤820（全局移动断点）隐藏父类名提示，节省空间让类目名完整显示 */
  display: none;
}

/* 桌面（与全局断点 820 对齐，替代原 640 私有断点）显示父类名提示 */
@media (min-width: 821px) {
  .cs-parent-hint {
    display: block;
  }
}

.cs-empty {
  padding: 1.5rem;
  text-align: center;
  color: var(--color-muted);
  font-size: 0.875rem;
}

/* Scrollbar styling */
.cs-options::-webkit-scrollbar {
  width: 6px;
}

.cs-options::-webkit-scrollbar-track {
  background: transparent;
}

.cs-options::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 3px;
}

.cs-options::-webkit-scrollbar-thumb:hover {
  background: var(--color-muted);
}
</style>
