import assert from 'node:assert/strict'
import test from 'node:test'

import { shouldUseNativeGallery } from '../src/utils/galleryMode.ts'

test('uses native image cards on mobile viewports', () => {
  assert.equal(shouldUseNativeGallery({ width: 390 }), true)
})

test('uses native image cards on tablet-sized viewports', () => {
  assert.equal(shouldUseNativeGallery({ width: 1023 }), true)
})

test('keeps the WebGL gallery on desktop viewports regardless of input settings', () => {
  assert.equal(shouldUseNativeGallery({ width: 1024 }), false)
  assert.equal(shouldUseNativeGallery({ width: 1280 }), false)
})
