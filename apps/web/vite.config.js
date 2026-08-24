import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: '../../dist',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
      '/livedoc': {
        target: process.env.VITE_LIVEDOC_TARGET || 'http://localhost:5174',
        changeOrigin: true
      }
    }
  }
})
