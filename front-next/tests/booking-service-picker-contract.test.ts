import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/BookingView.vue', import.meta.url), 'utf8')

test('booking services use a compact category-filtered dropdown instead of an expanded catalog', () => {
  assert.match(source, /class="service-picker-trigger"/)
  assert.match(source, /class="service-picker-panel"/)
  assert.match(source, /serviceCategories/)
  assert.match(source, /filteredServices/)
  assert.match(source, /serviceCategory\(service\)/)
  assert.doesNotMatch(source, /class="service-options"/)
})

test('booking service picker exposes accessible open, selection, and dismissal semantics', () => {
  assert.match(source, /aria-haspopup="dialog"/)
  assert.match(source, /:aria-expanded="servicePickerOpen"/)
  assert.match(source, /aria-label="护理项目类别"/)
  assert.match(source, /role="listbox"/)
  assert.match(source, /:aria-selected="selectedServiceId === service\.id"/)
  assert.match(source, /event\.key === 'Escape'/)
  assert.match(source, /servicePickerButton\.value\?\.focus\(\)/)
})

test('booking service picker stays bounded and responsive', () => {
  assert.match(source, /\.service-picker-panel\s*\{[^}]*max-height:/s)
  assert.match(source, /\.service-picker-list\s*\{[^}]*overflow-y:\s*auto/s)
  assert.match(source, /@media \(max-width: 640px\)[\s\S]*\.service-picker-list\s*\{[^}]*grid-template-columns:\s*1fr/)
})
