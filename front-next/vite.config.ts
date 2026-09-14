import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: process.env.VITE_BASE_PATH || '/',
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 8082,
    proxy: {
      '/face-next': {
        target: process.env.VITE_CHAIN_PROXY_TARGET || 'http://127.0.0.1:8090',
        changeOrigin: true,
      },
    },
  },
})
