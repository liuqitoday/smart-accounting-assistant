<template>
  <UiModal v-model="modalOpen" :title="state.title" size="sm">
    <p class="confirm-message">{{ state.message }}</p>
    <div class="confirm-actions">
      <button ref="cancelButton" class="button secondary" type="button" @click="settle(false)">
        {{ state.cancelText }}
      </button>
      <button ref="confirmButton" class="button" :class="{ danger: state.danger }" type="button" @click="settle(true)">
        {{ state.confirmText }}
      </button>
    </div>
  </UiModal>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import UiModal from '@/components/UiModal.vue'
import { useConfirmHost } from '@/composables/useConfirm'

const { state, settle } = useConfirmHost()

const cancelButton = ref<HTMLButtonElement | null>(null)
const confirmButton = ref<HTMLButtonElement | null>(null)

const modalOpen = computed({
  get: () => state.open,
  set: value => {
    // 背板点击 / 右上角关闭 → 视为取消
    if (!value) settle(false)
  }
})

watch(
  () => state.open,
  open => {
    if (open) {
      document.addEventListener('keydown', handleKeydown)
      void nextTick(() => {
        // danger 场景默认聚焦「取消」，避免回车误确认破坏性操作
        const target = state.danger ? cancelButton.value : confirmButton.value
        target?.focus()
      })
    } else {
      document.removeEventListener('keydown', handleKeydown)
    }
  }
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
})

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    settle(false)
  }
}
</script>

<style scoped>
.confirm-message {
  margin: 0 0 var(--space-lg);
  color: var(--color-text);
  line-height: 1.6;
  white-space: pre-line;
  overflow-wrap: break-word;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 820px) {
  .confirm-actions .button {
    flex: 1;
  }
}
</style>
