import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('WEB-R admin drawer exposes complete keyboard and route semantics', () => {
  const shell = read('../src/components/AppShell.vue')
  const css = read('../src/styles/app.css')

  assert.match(shell, /class="skip-link"/)
  assert.match(shell, /aria-expanded/)
  assert.match(shell, /aria-controls="admin-sidebar"/)
  assert.match(shell, /@keydown\.esc/)
  assert.match(shell, /watch\(/)
  assert.match(shell, /id="main-content"[^>]*tabindex="-1"/)
  assert.doesNotMatch(shell, /SC7 数据迁移与运营完善/)
  assert.match(css, /body\.mobile-nav-open/)
  assert.match(css, /min-height:\s*44px/)
})
