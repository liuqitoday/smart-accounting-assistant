import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'test-pwa-register',
      // The service-worker registration module is provided by VitePWA in
      // production and mocked by component tests without installing a worker.
      resolveId(id) {
        if (id === 'virtual:pwa-register/vue') return id
      },
    },
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
