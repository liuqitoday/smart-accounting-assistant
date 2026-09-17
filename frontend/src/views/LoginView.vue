<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-brand">
        <span class="auth-logo"><Wallet /></span>
        <div>
          <h1>记账助手</h1>
          <p>一句话记账,AI 帮你分好类。</p>
        </div>
      </div>

      <MessageBanner :message="message.text" :type="message.type" />

      <div ref="authTabsRef" class="auth-tabs t-tabs" role="tablist">
        <span class="t-tabs-pill" aria-hidden="true"></span>
        <button id="auth-tab-login" class="t-tab" type="button" role="tab" aria-controls="auth-tab-panel" :tabindex="mode === 'login' ? 0 : -1" :aria-selected="mode === 'login'" @click="setMode('login')" @keydown="handleAuthTabKeydown">登录</button>
        <button id="auth-tab-register" class="t-tab" type="button" role="tab" aria-controls="auth-tab-panel" :tabindex="mode === 'register' ? 0 : -1" :aria-selected="mode === 'register'" @click="setMode('register')" @keydown="handleAuthTabKeydown">注册</button>
      </div>

      <form id="auth-tab-panel" class="grid" role="tabpanel" :aria-labelledby="`auth-tab-${mode}`" @submit.prevent="submit">
        <div class="form-field">
          <label for="username">用户名</label>
          <input id="username" v-model.trim="form.username" class="input" autocomplete="username" required />
        </div>
        <div class="form-field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="form.password"
            class="input"
            type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            :minlength="mode === 'register' ? 10 : undefined"
            required
          />
          <p v-if="mode === 'register'" class="field-hint">至少 10 位，需同时包含字母和数字</p>
        </div>
        <button class="button" type="submit" :disabled="submitting">
          <span v-if="submitting" class="t-shimmer button-shimmer" data-text="处理中...">处理中...</span>
          <template v-else>{{ mode === 'login' ? '登录' : '创建账号' }}</template>
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Wallet } from 'lucide-vue-next'
import MessageBanner from '@/components/MessageBanner.vue'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useLedgerStore } from '@/stores/ledger'
import { useSlidingTabs } from '@/utils/slidingTabs'

const router = useRouter()
const auth = useAuthStore()
const ledger = useLedgerStore()
const mode = ref<'login' | 'register'>('login')
const submitting = ref(false)
const form = reactive({
  username: '',
  password: ''
})
const message = reactive<{ text: string; type: 'success' | 'error' | 'info' }>({
  text: '',
  type: 'info'
})
const { tabsRef: authTabsRef, syncTabs: syncAuthTabs, handleTabKeydown: handleAuthTabKeydown } = useSlidingTabs(index => {
  setMode(index === 0 ? 'login' : 'register')
})

function setMode(value: 'login' | 'register'): void {
  if (mode.value === value) return
  mode.value = value
  syncAuthTabs()
}

async function submit(): Promise<void> {
  message.text = ''
  if (mode.value === 'register' && (form.password.length < 10 || !/[A-Za-z]/.test(form.password) || !/[0-9]/.test(form.password))) {
    message.type = 'error'
    message.text = '密码至少 10 位，且需同时包含字母和数字'
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(form.username, form.password)
    } else {
      await auth.register(form.username, form.password)
    }
    ledger.reset()
    await ledger.load()
    await router.replace({ name: ledger.state.ledgers.length ? 'dashboard' : 'ledgers' })
  } catch (error) {
    message.type = 'error'
    message.text = error instanceof ApiError ? error.message : '操作失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-muted);
}

.auth-page {
  display: grid;
  min-height: 100vh;
  min-height: 100dvh;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(900px 460px at 14% -8%, rgba(242, 163, 60, 0.22), transparent 60%),
    radial-gradient(760px 420px at 96% 104%, rgba(235, 94, 40, 0.16), transparent 58%),
    var(--color-background);
}

.auth-panel {
  width: min(440px, 100%);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
  padding: 30px;
}

.auth-brand {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 24px;
}

.auth-logo {
  display: grid;
  place-items: center;
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: 15px;
  background: linear-gradient(135deg, #f4793f, var(--color-primary) 60%, var(--color-primary-dark));
  color: #fff6ec;
  box-shadow: var(--shadow-soft);
}

.auth-logo svg {
  width: 24px;
  height: 24px;
}

.auth-brand h1 {
  margin: 0;
  font-size: 27px;
}

.auth-brand p {
  margin: 8px 0 0;
  color: var(--color-muted);
}

.auth-tabs {
  --tabs-pill-bg: var(--color-surface);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  border-radius: 999px;
  background: var(--color-surface-soft);
  padding: 5px;
  margin-bottom: 18px;
}

.auth-tabs button {
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--color-muted);
  font-weight: 800;
  height: 34px;
  padding: 6px 14px;
}

.auth-tabs .t-tabs-pill {
  top: 5px;
  height: 34px;
  box-shadow: 0 1px 8px rgba(141, 98, 44, 0.14);
}

.auth-tabs button[aria-selected="true"] {
  color: var(--color-primary-text);
}

@media (max-width: 480px) {
  .auth-page {
    padding: 14px;
  }

  .auth-panel {
    padding: 22px;
  }
}
</style>
