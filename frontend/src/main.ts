import { createApp } from 'vue'
import { setUnauthorizedHandler } from '@/api/http'
import App from '@/App.vue'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { useLedgerStore } from '@/stores/ledger'
import '@/styles.css'

setUnauthorizedHandler(() => {
  useAuthStore().forceLogout()
  useLedgerStore().reset()
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login' })
  }
})

createApp(App).use(router).mount('#app')
