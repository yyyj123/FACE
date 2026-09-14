import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

test('customer booking explains the configured appointment-start interval separately from service duration', () => {
  const source = readFileSync(new URL('../src/views/BookingView.vue', import.meta.url), 'utf8')

  assert.match(source, /formatDuration\(rule\.slot_interval_minutes\)/)
  assert.match(source, /护理时长按所选项目计算/)
})

test('customer booking presents policy durations in readable hours without losing partial hours', () => {
  const source = readFileSync(new URL('../src/views/BookingView.vue', import.meta.url), 'utf8')

  assert.match(source, /const hours = Math\.floor\(minutes \/ 60\)/)
  assert.match(source, /const remainingMinutes = minutes % 60/)
  assert.match(source, /\$\{hours\} 小时 \$\{remainingMinutes\} 分钟/)
  assert.match(source, /formatDuration\(rule\.minimum_advance_minutes\)/)
  assert.match(source, /formatDuration\(rule\.free_cancel_minutes\)/)
  assert.match(source, /formatDuration\(rule\.reschedule_cutoff_minutes\)/)
})

test('customer booking explains that the 15-minute hold temporarily reserves the selected arrival slot', () => {
  const source = readFileSync(new URL('../src/views/BookingView.vue', import.meta.url), 'utf8')

  assert.match(source, /先暂时保留所选到店时段 15 分钟，再确认当前版本条款并结算/)
  assert.match(source, /暂时保留所选时段 15 分钟/)
})
