import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

test('member identity uses the V3 phone and SMS endpoints without the retired register API', () => {
  const api = readFileSync(new URL('../src/api/client.ts', import.meta.url), 'utf8')
  const login = readFileSync(new URL('../src/views/LoginView.vue', import.meta.url), 'utf8')

  for (const endpoint of [
    '/client/identity/password-login',
    '/client/identity/sms-login',
    '/client/identity/register',
    '/client/identity/password-reset',
    '/client/identity/sms/request',
  ]) assert.match(api, new RegExp(endpoint.replaceAll('/', '\\/')))
  assert.doesNotMatch(api, /http\.post\('\/auth\/register'/)
  assert.match(login, /手机号/)
  assert.match(login, /验证码/)
  assert.match(login, /忘记密码/)
})
