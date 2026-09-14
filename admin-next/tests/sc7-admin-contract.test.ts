import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

test('SC7 exposes desktop import and mobile high-frequency operations without mobile batch actions', () => {
  const api = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8')
  const router = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
  const shell = readFileSync(new URL('../src/components/AppShell.vue', import.meta.url), 'utf8')
  const migration = readFileSync(new URL('../src/views/DataMigrationView.vue', import.meta.url), 'utf8')
  const workbench = readFileSync(new URL('../src/views/MobileOperationsView.vue', import.meta.url), 'utf8')

  assert.match(api, /X-FACE-Admin-Surface': 'DESKTOP'/)
  assert.match(api, /\/api\/v3\/legacy-import\/preflight/)
  assert.match(router, /name: 'data-migration'/)
  assert.match(router, /name: 'workbench'/)
  assert.match(shell, /desktop-only-nav/)
  assert.match(migration, /@media \(max-width: 900px\) \{ \.migration-page \{ display: none;/)
  assert.match(workbench, /今日预约/)
  assert.match(workbench, /技师日程/)
  assert.match(workbench, /订单与发货/)
  assert.match(workbench, /售后沟通/)
  assert.doesNotMatch(workbench, /正式导入/)
})
