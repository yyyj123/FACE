import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('WEB-R customer shell is keyboard and mobile safe without technician navigation', () => {
  const shell = read('../src/components/ClientShell.vue')
  const css = read('../src/styles/app.css')

  assert.match(shell, /class="skip-link"/)
  assert.match(shell, /id="main-content"[^>]*tabindex="-1"/)
  assert.match(shell, /watch\(/)
  assert.doesNotMatch(shell, /auth\.isTechnician|to="\/workbench"|技师工作台/)
  assert.match(css, /safe-area-inset-bottom/)
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/)
})

test('WEB-R customer pages are route-level chunks', () => {
  const router = read('../src/router/index.ts')
  assert.doesNotMatch(router, /import HomeView from/)
  assert.doesNotMatch(router, /import BookingView from/)
  assert.match(router, /component: \(\) => import\('\.\.\/views\/HomeView\.vue'\)/)
  assert.match(router, /component: \(\) => import\('\.\.\/views\/BookingView\.vue'\)/)
})
