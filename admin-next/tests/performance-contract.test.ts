import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const router = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')

test('protected route code loads in parallel with the initial account context', () => {
  assert.match(router, /function preloadRouteComponents/)
  assert.match(router, /Promise\.all\(\[auth\.refreshContext\(\), componentPreload\]\)/)
  assert.match(router, /const loadAppShell = \(\) => import/)
  assert.match(router, /const routeLoaders = new Map/)
})

test('booking operations can shrink while its weekly table scrolls internally', () => {
  const booking = readFileSync(new URL('../src/views/BookingOperationsView.vue', import.meta.url), 'utf8')
  assert.match(booking, /\.booking-ops\{grid-template-columns:minmax\(0,1fr\)\}/)
  assert.match(booking, /\.booking-ops\{display:grid;min-width:0;/)
  assert.match(booking, /\.admin-week-wrap\{overflow:auto;/)
})
