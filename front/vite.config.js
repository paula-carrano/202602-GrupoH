import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/test/**'],
      reporter: ['text-summary', ['lcov', { projectRoot: fileURLToPath(new URL('..', import.meta.url)) }]],
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
