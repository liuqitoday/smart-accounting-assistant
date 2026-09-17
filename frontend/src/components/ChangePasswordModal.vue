<template>
  <UiModal v-model="open" title="修改密码" subtitle="输入当前密码后设置新密码。" size="sm">
    <form class="form-grid" @submit.prevent="submit">
      <div class="form-field full">
        <label for="current-password">当前密码</label>
        <input id="current-password" v-model="form.currentPassword" class="input" type="password" autocomplete="current-password" required />
      </div>
      <div class="form-field full">
        <label for="new-password">新密码</label>
        <input id="new-password" v-model="form.newPassword" class="input" type="password" autocomplete="new-password" minlength="10" required />
      </div>
      <div class="form-field full">
        <label for="confirm-password">确认新密码</label>
        <input id="confirm-password" v-model="form.confirmPassword" class="input" type="password" autocomplete="new-password" minlength="10" required />
      </div>

      <div class="form-field full">
        <MessageBanner :message="message.text" :type="message.type" />
      </div>

      <div class="form-field full row-actions">
        <button class="button" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存新密码' }}</button>
        <button class="button secondary" type="button" @click="close">取消</button>
      </div>
    </form>
  </UiModal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { authApi } from '@/api'
import { ApiError } from '@/api/http'
import MessageBanner from '@/components/MessageBanner.vue'
import UiModal from '@/components/UiModal.vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const open = ref(props.modelValue)
const saving = ref(false)
const message = reactive<{ text: string; type: 'success' | 'error' | 'info' }>({ text: '', type: 'info' })
const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

watch(
  () => props.modelValue,
  value => {
    open.value = value
    if (value) {
      reset()
    }
  },
  { immediate: true }
)

watch(open, value => {
  emit('update:modelValue', value)
})

async function submit(): Promise<void> {
  message.text = ''
  if (!validate()) return

  saving.value = true
  try {
    await authApi.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    close()
    emit('changed')
  } catch (error) {
    message.type = 'error'
    message.text = error instanceof ApiError ? error.message : '修改失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

function close(): void {
  open.value = false
}

function validate(): boolean {
  if (!form.currentPassword || !form.newPassword || !form.confirmPassword) {
    message.type = 'error'
    message.text = '请完整填写密码信息'
    return false
  }
  if (form.newPassword.length < 10 || !/[A-Za-z]/.test(form.newPassword) || !/[0-9]/.test(form.newPassword)) {
    message.type = 'error'
    message.text = '新密码至少 10 位，且需同时包含字母和数字'
    return false
  }
  if (form.currentPassword === form.newPassword) {
    message.type = 'error'
    message.text = '新密码不能与当前密码相同'
    return false
  }
  if (form.newPassword !== form.confirmPassword) {
    message.type = 'error'
    message.text = '两次输入的新密码不一致'
    return false
  }
  return true
}

function reset(): void {
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  message.text = ''
  message.type = 'info'
}
</script>
