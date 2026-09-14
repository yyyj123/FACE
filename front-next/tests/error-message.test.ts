import assert from 'node:assert/strict'
import test from 'node:test'

import { toRequestError } from '../src/api/errors.ts'

test('uses the backend business message when it is available', () => {
  const error = toRequestError({
    response: { status: 400, data: { code: 400, msg: '账号长度应为 3 到 80 个字符' } },
  })

  assert.equal(error.message, '账号长度应为 3 到 80 个字符')
})

test('does not expose the generic axios 500 message', () => {
  const error = toRequestError({
    message: 'Request failed with status code 500',
    response: { status: 500, data: {} },
  })

  assert.equal(error.message, '服务暂时不可用，请稍后重试')
})

test('gives a useful message when the service cannot be reached', () => {
  const error = toRequestError({ message: 'Network Error', request: {} })

  assert.equal(error.message, '无法连接服务，请检查网络后重试')
})
