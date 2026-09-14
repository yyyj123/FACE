const args = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...parts] = entry.replace(/^--/, '').split('=')
  return [key, parts.join('=')]
}))

const backend = args.backend ?? 'http://127.0.0.1:8199/face-next'
const concurrency = Number(args.concurrency ?? 20)
const requestCount = Number(args.requests ?? 500)
const p95Limit = Number(args['p95-ms'] ?? 500)
const p99Limit = Number(args['p99-ms'] ?? 1000)

if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 100) throw new Error('Invalid concurrency.')
if (!Number.isInteger(requestCount) || requestCount < concurrency) throw new Error('Invalid request count.')

async function json(path, { method = 'GET', token, headers = {}, body, expected = [200] } = {}) {
  const response = await fetch(`${backend}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let payload
  try { payload = JSON.parse(text) } catch { payload = undefined }
  if (!expected.includes(response.status)) {
    throw new Error(`${method} ${path} returned ${response.status}: ${payload?.message ?? text.slice(0, 160)}`)
  }
  return { response, payload, text }
}

const login = await json('/api/v3/auth/login', {
  method: 'POST', body: { username: 'admin', password: 'Face@123' },
})
const token = login.payload?.data?.access_token
if (!token) throw new Error('Capacity verifier could not obtain an admin session.')

const readiness = await json('/actuator/health/readiness')
if (readiness.payload?.status !== 'UP') throw new Error('Readiness is not UP before capacity verification.')
const securityHeaders = {
  contentTypeOptions: readiness.response.headers.get('x-content-type-options'),
  frameOptions: readiness.response.headers.get('x-frame-options'),
}
if (securityHeaders.contentTypeOptions !== 'nosniff' || !securityHeaders.frameOptions) {
  throw new Error(`Required browser security headers are incomplete: ${JSON.stringify(securityHeaders)}`)
}

await json('/api/v3/open/v1/catalog/services', {
  headers: {
    Authorization: 'Bearer synthetic-invalid-secret',
    'X-Integration-Client': 'SYNTHETIC_INVALID',
    'X-Integration-Nonce': 'synthetic-missing-timestamp',
  },
  expected: [400],
})

const paths = [
  '/api/v3/training/courses?shop_id=1',
  '/api/v3/training/records?shop_id=1',
  '/api/v3/marketing/campaigns?shop_id=1&page=1&page_size=20',
]
for (let index = 0; index < 30; index += 1) {
  await json(paths[index % paths.length], { token })
}

const timings = []
const failures = []
let cursor = 0
async function worker() {
  while (true) {
    const index = cursor
    cursor += 1
    if (index >= requestCount) return
    const started = performance.now()
    try {
      const result = await json(paths[index % paths.length], { token })
      const forbidden = /"(?:phone|idCard|id_card|health|diagnosis|paymentCredential|payment_credential)"\s*:/i
      if (forbidden.test(result.text)) throw new Error('Read response contains a forbidden sensitive field.')
      timings.push(performance.now() - started)
    } catch (error) {
      failures.push(String(error?.message ?? error))
    }
  }
}

const totalStarted = performance.now()
await Promise.all(Array.from({ length: concurrency }, () => worker()))
const totalMilliseconds = performance.now() - totalStarted
const sorted = timings.toSorted((left, right) => left - right)
const percentile = (value) => sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * value) - 1)] ?? Infinity
const p50 = percentile(0.50)
const p95 = percentile(0.95)
const p99 = percentile(0.99)
const requestsPerSecond = requestCount / (totalMilliseconds / 1000)

if (failures.length > 0 || timings.length !== requestCount) {
  throw new Error(`Capacity requests failed: ${JSON.stringify(failures.slice(0, 5))}`)
}
if (p95 > p95Limit || p99 > p99Limit) {
  throw new Error(`Capacity threshold failed: p95=${p95.toFixed(2)}ms p99=${p99.toFixed(2)}ms`)
}

console.log(`M6_CAPACITY_CONCURRENCY=${concurrency}`)
console.log(`M6_CAPACITY_REQUESTS=${requestCount}`)
console.log('M6_CAPACITY_ERRORS=0')
console.log(`M6_CAPACITY_P50_MS=${p50.toFixed(2)}`)
console.log(`M6_CAPACITY_P95_MS=${p95.toFixed(2)}`)
console.log(`M6_CAPACITY_P99_MS=${p99.toFixed(2)}`)
console.log(`M6_CAPACITY_RPS=${requestsPerSecond.toFixed(2)}`)
console.log('M6_SECURITY_HEADERS=PASS')
console.log('M6_OPEN_MISSING_TIMESTAMP_GUARD=PASS')
console.log('M6_CAPACITY_SECURITY=PASS')
