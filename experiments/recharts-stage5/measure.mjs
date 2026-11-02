import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'
import { execSync } from 'node:child_process'
import { createHash } from 'node:crypto'
const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../..')
const size = dir => readdirSync(dir, { recursive: true }).filter(f => /\.(js|css)$/.test(f)).map(file => {
  const bytes = readFileSync(resolve(dir, file))
  return { file: file.replaceAll('\\', '/'), bytes: bytes.length, gzip: gzipSync(bytes).length }
})
const builds = {}
for (const mode of ['app', 'baseline', 'recharts']) {
  // Lista cerrada, mismo comando y configuración; solo cambia el entry/renderizador.
  const log = execSync(`npm run build -- --mode ${mode}`, { cwd: here, encoding: 'utf8' })
  writeFileSync(resolve(here, `build-${mode}.log`), log)
  const files = size(resolve(here, `dist-${mode}`))
  builds[mode] = { files, js: files.filter(f => f.file.endsWith('.js')).reduce((a, f) => ({ bytes: a.bytes + f.bytes, gzip: a.gzip + f.gzip }), { bytes: 0, gzip: 0 }), css: files.filter(f => f.file.endsWith('.css')).reduce((a, f) => ({ bytes: a.bytes + f.bytes, gzip: a.gzip + f.gzip }), { bytes: 0, gzip: 0 }) }
}
const original = JSON.parse(readFileSync(resolve(root, 'front/package-lock.json'))).packages
const trial = JSON.parse(readFileSync(resolve(here, 'package-lock.json'))).packages
const addedPackages = Object.entries(trial).filter(([key]) => key && !original[key]).map(([path, value]) => ({ path, version: value.version }))
const changedPackages = Object.entries(trial).filter(([key, value]) => key && original[key] && original[key].version !== value.version).map(([path, value]) => ({ path, before: original[path].version, after: value.version }))
const initial = JSON.parse(readFileSync(resolve(here, 'initial-state.json')))
const changedOriginalFiles = Object.entries(initial.files).filter(([file, hash]) => !existsSync(resolve(root, file)) || createHash('sha256').update(readFileSync(resolve(root, file))).digest('hex') !== hash).map(([file]) => file)
const result = { node: process.version, builds, deltaRenderer: { bytes: builds.recharts.js.bytes - builds.baseline.js.bytes, gzip: builds.recharts.js.gzip - builds.baseline.js.gzip }, deltaFromApp: { bytes: builds.recharts.js.bytes - builds.app.js.bytes, gzip: builds.recharts.js.gzip - builds.app.js.gzip }, addedPackages, changedPackages, changedOriginalFiles }
writeFileSync(resolve(here, 'measurement.json'), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify(result, null, 2))
if (changedOriginalFiles.length || result.deltaRenderer.bytes <= 0) process.exitCode = 1
