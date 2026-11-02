import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
const local = path => fileURLToPath(new URL(path, import.meta.url))
export default defineConfig(({ mode }) => ({
  root: local('./.work'),
  plugins: [react(), {
    name: 'isolated-evaluation-entry',
    transformIndexHtml: { order: 'pre', handler: html => html.replace('/src/main.jsx', mode === 'app' ? '/base.jsx' : '/lab.jsx') },
  }],
  resolve: { alias: { '@lab-renderer': local(mode === 'baseline' ? './TableOnly.jsx' : './Charts.jsx') } },
  build: { outDir: local(`./dist-${mode}`), emptyOutDir: true },
}))
