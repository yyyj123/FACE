import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

test('booking policy UI keeps appointment starts aligned to the hourly schedule', () => {
  const source = readFileSync(new URL('../src/views/BookingOperationsView.vue', import.meta.url), 'utf8')

  assert.match(source, /slotIntervalMinutes: 60/)
  assert.match(source, /60 分钟（与排班表一致）/)
  assert.match(source, /护理时长仍按项目实际分钟数占用/)
  assert.doesNotMatch(source, /\[15,20,30,60\]/)
})
