<template>
  <AppShell>
    <PageHeader title="标签管理" subtitle="维护当前账本的交易标签，用于筛选、导入和统计。">
      <template #actions>
        <button class="button" type="button" :disabled="!ledger.canEdit.value" @click="openCreate">
          <Plus />
          新建标签
        </button>
      </template>
    </PageHeader>

    <MessageBanner :message="message.text" :type="message.type" @dismissed="clear" />

    <div v-if="loading" class="loading-state">
      <span class="t-shimmer" data-text="加载中...">加载中...</span>
    </div>
    <div v-else-if="!tags.length" class="empty-state tags">还没有标签,建一个给账单做记号吧</div>
    <div v-else class="grid cols-3">
      <article v-for="tag in tags" :key="tag.id" class="card tag-card">
        <div class="tag-title">
          <span class="tag-dot large" :style="{ background: tag.color || '#95876F' }" />
          <div>
            <h2>{{ tag.name }}</h2>
            <p>{{ tag.system ? '系统标签' : '自定义标签' }}</p>
          </div>
        </div>
        <div class="row-actions">
          <button class="button secondary compact" type="button" :disabled="tag.system || !ledger.canEdit.value" @click="openEdit(tag)">编辑</button>
          <button class="button danger compact" type="button" :disabled="tag.system || !ledger.canEdit.value" @click="remove(tag)">删除</button>
        </div>
      </article>
    </div>

    <UiModal v-model="modalOpen" :title="editingId ? '编辑标签' : '新建标签'" size="sm">
      <form class="grid" @submit.prevent="save">
        <div class="form-field">
          <label for="tag-name">标签名称</label>
          <input id="tag-name" v-model.trim="form.name" class="input" required maxlength="50" />
        </div>
        <div class="form-field">
          <span id="tag-color-label" class="field-label">颜色</span>
          <ColorSwatches v-model="form.color" labelledby="tag-color-label" />
        </div>
        <MessageBanner :message="modalMessage.text" :type="modalMessage.type" @dismissed="clearModalMessage" />
        <div class="row-actions">
          <button class="button" type="submit" :disabled="saving">
            <span v-if="saving" class="t-shimmer button-shimmer" data-text="保存中...">保存中...</span>
            <template v-else>保存</template>
          </button>
          <button class="button secondary" type="button" @click="modalOpen = false">取消</button>
        </div>
      </form>
    </UiModal>
  </AppShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import { tagsApi } from '@/api'
import AppShell from '@/components/AppShell.vue'
import ColorSwatches from '@/components/ColorSwatches.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import PageHeader from '@/components/PageHeader.vue'
import UiModal from '@/components/UiModal.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useMessage } from '@/composables/useMessage'
import { useLedgerStore } from '@/stores/ledger'
import type { Tag } from '@/types'

const ledger = useLedgerStore()
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | null>(null)
const tags = ref<Tag[]>([])
const form = reactive({
  name: '',
  color: '#EB5E28'
})
const { message, showSuccess, showError, clear } = useMessage()
const { message: modalMessage, showError: showModalError, clear: clearModalMessage } = useMessage()
const { confirm } = useConfirm()

onMounted(loadTags)

async function loadTags(): Promise<void> {
  loading.value = true
  try {
    tags.value = await tagsApi.list()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.color = '#EB5E28'
  clearModalMessage()
  modalOpen.value = true
}

function openEdit(tag: Tag): void {
  editingId.value = tag.id
  form.name = tag.name
  form.color = tag.color || '#EB5E28'
  clearModalMessage()
  modalOpen.value = true
}

async function save(): Promise<void> {
  saving.value = true
  clearModalMessage()
  try {
    if (editingId.value) {
      await tagsApi.update(editingId.value, form)
      showSuccess('标签已更新。')
    } else {
      await tagsApi.create(form)
      showSuccess('标签已创建。')
    }
    modalOpen.value = false
    await loadTags()
  } catch (error) {
    showModalError(error)
  } finally {
    saving.value = false
  }
}

async function remove(tag: Tag): Promise<void> {
  if (!(await confirm({ title: '删除标签', message: `确认删除标签「${tag.name}」？`, danger: true }))) return
  try {
    await tagsApi.delete(tag.id)
    showSuccess('标签已删除。')
    await loadTags()
  } catch (error) {
    showError(error)
  }
}
</script>

<style scoped>
.tag-card {
  display: grid;
  gap: var(--space-md);
}

.tag-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tag-title h2 {
  margin: 0;
  font-size: 18px;
}

.tag-title p {
  margin: 4px 0 0;
  color: var(--color-muted);
}

.tag-dot.large {
  width: 24px;
  height: 24px;
}
</style>
