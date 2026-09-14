import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

test('admin UI exposes only the SC2 operating surfaces and visible roles', () => {
  const api = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8')
  const router = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
  const shell = readFileSync(new URL('../src/components/AppShell.vue', import.meta.url), 'utf8')

  assert.match(api, /\/api\/v3\/auth\/admin-login/)
  assert.match(api, /\/api\/v3\/content/)
  assert.match(api, /\/api\/v3\/admin-accounts/)
  assert.match(router, /name: 'content'/)
  assert.match(router, /name: 'settings'/)
  assert.match(shell, /内容运营/)
  assert.match(shell, /系统设置/)
})
