import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('staff cards link to a dedicated responsive detail page', () => {
  const home = read('../src/views/HomeView.vue')
  const detail = read('../src/views/StaffDetailView.vue')
  const router = read('../src/router/index.ts')

  assert.match(home, /name: 'staff-detail'/)
  assert.doesNotMatch(home, /person\.specialties \|\| person\.bio/)
  assert.match(router, /path: 'staff\/:id'/)
  assert.match(detail, /擅长项目/)
  assert.match(detail, /name: 'booking'.*staffId/s)
  assert.match(detail, /@media\(max-width:760px\)/)
})

test('booking honors a technician requested from the detail page', () => {
  const booking = read('../src/views/BookingView.vue')
  assert.match(booking, /route\.query\.staffId/)
  assert.match(booking, /assignmentMode\.value = 'SPECIFIED'/)
})
