import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const home = readFileSync(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8')

test('home staff gallery renders five technicians per accessible page', () => {
  assert.match(home, /const STAFF_PAGE_SIZE = 5/)
  assert.match(home, /staff\.value\.slice\(start, start \+ STAFF_PAGE_SIZE\)/)
  assert.match(home, /v-for="person in pagedStaff"/)
  assert.match(home, /aria-label="护理技师分页"/)
  assert.match(home, /:aria-current="staffPage === page \? 'page' : undefined"/)
  assert.match(home, /\.staff-grid\s*\{[^}]*grid-template-columns:\s*repeat\(5, minmax\(0, 1fr\)\)/s)
  assert.match(home, /@media \(max-width: 900px\)[\s\S]*?\.staff-grid\s*\{[^}]*repeat\(3, minmax\(0, 1fr\)\)/)
  assert.match(home, /@media \(max-width: 640px\)[\s\S]*?\.staff-grid\s*\{\s*grid-template-columns:\s*1fr 1fr/)
})
