// Copia de evaluación: nunca escribe en front ni copia archivos de entorno.
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
const here = dirname(fileURLToPath(import.meta.url))
const repo = resolve(here, '../..')
const front = resolve(repo, 'front')
const hash = file => createHash('sha256').update(readFileSync(resolve(repo, file))).digest('hex')
const existing = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { cwd: repo }).toString().split('\0').filter(f => f && !f.startsWith('experiments/recharts-stage5/'))
if (!existsSync(resolve(here, 'initial-state.json'))) writeFileSync(resolve(here, 'initial-state.json'), JSON.stringify({
  gitStatus: execFileSync('git', ['status', '--short'], { cwd: repo }).toString(),
  files: Object.fromEntries(existing.filter(f => existsSync(resolve(repo, f))).map(f => [f, hash(f)])),
}, null, 2) + '\n')
mkdirSync(resolve(here, '.work'), { recursive: true })
cpSync(resolve(front, 'src'), resolve(here, '.work/src'), { recursive: true })
cpSync(resolve(front, 'index.html'), resolve(here, '.work/index.html'))
if (existsSync(resolve(front, 'public'))) cpSync(resolve(front, 'public'), resolve(here, '.work/public'), { recursive: true })
const main = readFileSync(resolve(front, 'src/main.jsx'), 'utf8').replace('"./App"', '"./src/App"').replace('"./style.css"', '"./src/style.css"')
writeFileSync(resolve(here, '.work/base.jsx'), main)
writeFileSync(resolve(here, '.work/lab.jsx'), main.replace('import { App }', 'import { Lab } from "../Lab.jsx";\nimport { App }').replace('<App />', '{window.location.pathname === "/__lab" ? <Lab /> : <App />}'))
if (!existsSync(resolve(here, 'package.json'))) {
  const pkg = JSON.parse(readFileSync(resolve(front, 'package.json')))
  pkg.name = 'grupo-h-recharts-stage5-isolated'
  pkg.scripts = { build: 'vite build', dev: 'vite --host 127.0.0.1 --port 5175 --strictPort --mode recharts', preview: 'vite preview --host 127.0.0.1 --port 5175 --strictPort --mode recharts', measure: 'node measure.mjs' }
  writeFileSync(resolve(here, 'package.json'), JSON.stringify(pkg, null, 2) + '\n')
  cpSync(resolve(front, 'package-lock.json'), resolve(here, 'package-lock.json'))
}
console.log('Snapshot listo en .work; front intacto.')
