import test, { afterEach } from 'node:test'
import assert from 'node:assert/strict'
import handler from '../api/proxy.mjs'

const originalFetch = globalThis.fetch
const originalBackend = process.env.BACKEND_API_URL
const originalViteBackend = process.env.VITE_API_BASE_URL
afterEach(() => {
  globalThis.fetch = originalFetch
  if (originalBackend === undefined) delete process.env.BACKEND_API_URL
  else process.env.BACKEND_API_URL = originalBackend
  if (originalViteBackend === undefined) delete process.env.VITE_API_BASE_URL
  else process.env.VITE_API_BASE_URL = originalViteBackend
})
function response() {
  return { headers: {}, code: 200, body: null,
    setHeader(name, value) { this.headers[name.toLowerCase()] = value },
    status(code) { this.code = code; return this },
    json(body) { this.body = body; return this },
    send(body) { this.body = body; return this },
  }
}
test('reuses the existing Vercel API environment when a dedicated upstream is unset', async () => {
  delete process.env.BACKEND_API_URL
  process.env.VITE_API_BASE_URL = 'https://existing-backend.example'
  let target
  globalThis.fetch = async url => { target = url.toString(); return new Response('{"status":"UP"}') }
  const result = response()
  await handler({ method: 'GET', query: { path: 'health' }, headers: {} }, result)
  assert.equal(target, 'https://existing-backend.example/api/health')
  assert.equal(result.code, 200)
})
test('forwards cookies, origin, CSRF header and JSON body to the fixed upstream', async () => {
  process.env.BACKEND_API_URL = 'https://backend.example'
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url: url.toString(), ...options }
    return new Response('{"role":"USER"}', { status: 201, headers: { 'content-type': 'application/json', 'set-cookie': 'cloudsql_session=opaque; HttpOnly; Secure; SameSite=Lax; Path=/' } })
  }
  const result = response()
  await handler({ method: 'POST', query: { path: 'auth/signup' }, headers: { cookie: 'cloudsql_session=previous', origin: 'https://frontend.example', 'content-type': 'application/json', 'x-cloudsql-request': '1' }, body: { email: 'a@example.test', password: 'secret' } }, result)
  assert.equal(captured.url, 'https://backend.example/api/auth/signup')
  assert.equal(captured.headers.get('cookie'), 'cloudsql_session=previous')
  assert.equal(captured.headers.get('origin'), 'https://frontend.example')
  assert.equal(captured.headers.get('x-cloudsql-request'), '1')
  assert.equal(captured.body, '{"email":"a@example.test","password":"secret"}')
  assert.equal(captured.redirect, 'manual')
  assert.equal(result.code, 201)
  assert.match(result.headers['set-cookie'][0], /HttpOnly; Secure/)
  assert.equal(result.headers['cache-control'], 'no-store')
})
test('preserves unauthorized responses and prevents caching', async () => {
  process.env.BACKEND_API_URL = 'https://backend.example'
  globalThis.fetch = async () => new Response('{"error":"Please sign in."}', { status: 401, headers: { 'cache-control': 'public, max-age=3600' } })
  const result = response()
  await handler({ method: 'GET', query: { path: 'dashboard' }, headers: {} }, result)
  assert.equal(result.code, 401)
  assert.equal(result.headers['cache-control'], 'no-store')
  assert.match(result.body.toString(), /Please sign in/)
})
test('rejects unrecognized paths and attacker-controlled destinations', async () => {
  globalThis.fetch = async () => { throw new Error('must not call upstream') }
  for (const path of ['https://evil.example', '../auth/me', 'auth/me?target=evil', ['auth', 'me'], 'proxy']) {
    const result = response()
    await handler({ method: 'GET', query: { path }, headers: {} }, result)
    assert.equal(result.code, 404)
  }
})
test('requires an HTTPS origin without embedded credentials or paths', async () => {
  for (const backend of ['http://backend.example', 'https://user:secret@backend.example', 'https://backend.example/api', 'not-a-url']) {
    process.env.BACKEND_API_URL = backend
    const result = response()
    await handler({ method: 'GET', query: { path: 'health' }, headers: {} }, result)
    assert.equal(result.code, 503)
    assert.doesNotMatch(JSON.stringify(result.body), /secret/)
  }
})
test('network errors return a controlled response without leaking configuration', async () => {
  process.env.BACKEND_API_URL = 'https://private-backend.example'
  globalThis.fetch = async () => { throw new Error('secret upstream failure') }
  const result = response()
  await handler({ method: 'GET', query: { path: 'problems/4' }, headers: {} }, result)
  assert.equal(result.code, 502)
  assert.doesNotMatch(JSON.stringify(result.body), /secret|private-backend/)
})
