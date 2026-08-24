import { defineConfig } from 'vite'

export default defineConfig({
  base: '/livedoc/',
  publicDir: 'public/livedoc',
  build: {
    outDir: '../../dist/livedoc',
    emptyOutDir: true
  },
  server: {
    port: 5174
  }
})
