import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

const options = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...rest] = entry.replace(/^--/, '').split('='); return [key, rest.join('=')]
}))
const { base, project, root } = options
const envFile = options['env-file']
if (!base || !project || !root || !envFile) throw new Error('SC8 runtime check requires base, project, root and env-file.')
const env = Object.fromEntries(readFileSync(envFile, 'utf8').split(/\r?\n/).filter((line) => /^[^#][^=]*=/.test(line)).map((line) => { const i=line.indexOf('='); return [line.slice(0,i).trim(),line.slice(i+1).trim()] }))
const expect = (condition, message) => { if (!condition) throw new Error(message) }

async function raw(path, init={}) { return fetch(`${base}${path}`, { redirect:'manual', ...init }) }
const home = await raw('/'); expect(home.status===200 && (await home.text()).includes('FACE'), 'Demo access page is unavailable.')
for (const path of ['/admin/','/client/']) {
  const response=await raw(path), location=response.headers.get('location')||''
  expect(response.status===302, `Unauthenticated ${path} must redirect to the gate.`)
  expect(location.startsWith('/?next=')&&!location.includes(':8080'), `Demo gate redirect must stay relative and hide the container port: ${location}`)
}
expect((await raw('/api/v3/health/readiness')).status===401, 'Unauthenticated API must be rejected.')

const session = await raw('/demo/session', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({password:env.FACE_DEMO_ACCESS_PASSWORD}) })
expect(session.status===200, `Demo access login returned ${session.status}.`)
const setCookie = session.headers.get('set-cookie') || ''
expect(/face_demo_access=/.test(setCookie), 'Demo access cookie is missing.')
expect(/Max-Age=43200/i.test(setCookie) && /HttpOnly/i.test(setCookie) && /Secure/i.test(setCookie) && /SameSite=Lax/i.test(setCookie), `Demo cookie contract is incomplete: ${setCookie}`)
const cookie = setCookie.split(';',1)[0]
const authHeaders = { Cookie:cookie }
expect((await raw('/admin/',{headers:authHeaders})).status===200, 'Authorized admin shell is unavailable.')
expect((await raw('/client/',{headers:authHeaders})).status===200, 'Authorized client shell is unavailable.')
expect((await raw('/api/v3/health/readiness',{headers:authHeaders})).status===200, 'Authorized unified API is unavailable.')

async function json(path, body) {
  const response=await raw(path,{method:'POST',headers:{...authHeaders,'Content-Type':'application/json'},body:JSON.stringify(body)})
  const payload=await response.json().catch(()=>({})); expect(response.status===200, `${path} returned ${response.status}: ${payload.message||''}`); return payload.data
}
for (const username of ['admin','demo-admin']) {
  const result=await json('/api/v3/auth/login',{username,password:env.FACE_DEMO_ADMIN_PASSWORD}); expect(result?.access_token, `Fixed admin login failed: ${username}`)
}
const member=await json('/api/v3/client/identity/password-login',{phone:'13900000001',password:env.FACE_DEMO_ADMIN_PASSWORD}); expect(member?.access_token, 'Fixed member login failed.')
const suffix=Date.now().toString().slice(-8), phone=`137${suffix}`
const sms=await json('/api/v3/client/identity/sms/request',{phone,purpose:'REGISTER_LOGIN'}); expect(sms?.demo_code==='888888', 'Demo SMS did not return the fixed code.')
const registered=await json('/api/v3/client/identity/register',{phone,code:'888888',password:'SC8-Registered-2026',name:'SC8 合成会员'}); expect(registered?.access_token, 'Demo registration failed.')

for (const stage of ['sc4','sc5','sc6']) {
  const args=[`${root}/scripts/${stage}-runtime-check.mjs`,`--client=${base}`,`--admin=${base}`,`--project=${project}`,`--root=${root}`,`--env-file=${envFile}`,`--secret=${env.FACE_PAYMENT_DEMO_MOCK_SECRET}`,`--cookie=${cookie}`,`--account-password=${env.FACE_DEMO_ADMIN_PASSWORD}`]
  const result=spawnSync(process.execPath,args,{encoding:'utf8',stdio:['ignore','pipe','pipe']})
  process.stdout.write(result.stdout||''); process.stderr.write(result.stderr||'')
  expect(result.status===0, `${stage.toUpperCase()} business flow failed.`)
}
console.log(JSON.stringify({gate:'PASS',cookie:'PASS',fixedAccounts:'PASS',smsRegistration:'PASS',appointmentCardsPointsMallFulfillmentReviewAftersale:'PASS'},null,2))
console.log('SC8_BUSINESS_RUNTIME=PASS')
