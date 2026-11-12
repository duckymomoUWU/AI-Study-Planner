import { test, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { register, login, getCurrentUser, logout, getCsrfToken, clearCsrfTokenCache, AuthApiError } from './auth.js'

beforeEach(() => {
  clearCsrfTokenCache()
})

function mockDocument(t, cookie) {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'document')
  Object.defineProperty(globalThis, 'document', { configurable: true, value: { cookie } })
  t.after(() => {
    if (original) Object.defineProperty(globalThis, 'document', original)
    else delete globalThis.document
  })
}

const csrfResponse = token => Response.json({ data: { token, headerName: 'X-CSRF-TOKEN' } })
const user = { id: 'user-uuid-1', email: 'test@example.com', createdAt: '2026-11-07T03:00:00Z' }

test('getCsrfToken requests /api/v1/auth/csrf, reads data.token and caches it', async t => {
  let callCount = 0
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/auth/csrf')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    callCount++
    return csrfResponse('mock-csrf-token-123')
  })

  assert.equal(await getCsrfToken(), 'mock-csrf-token-123')
  assert.equal(await getCsrfToken(), 'mock-csrf-token-123')
  assert.equal(callCount, 1, 'Token should be cached after first request')
})

test('register fetches CSRF, posts to /api/v1/auth/register and unwraps data', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return csrfResponse('csrf-abc')
    }
    if (url === '/api/v1/auth/register') {
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'same-origin')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-abc')
      const body = JSON.parse(options.body)
      assert.equal(body.email, 'test@example.com')
      assert.equal(body.password, 'password123')
      return Response.json({ data: user }, { status: 201 })
    }
    throw new Error(`Unexpected url ${url}`)
  })

  const created = await register('test@example.com', 'password123')
  assert.equal(created.id, 'user-uuid-1')
  assert.equal(created.email, 'test@example.com')
})

test('login sends credentials and updates the cached CSRF token if returned in a header', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return csrfResponse('csrf-initial')
    }
    if (url === '/api/v1/auth/login') {
      assert.equal(options.method, 'POST')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-initial')
      return new Response(JSON.stringify({ data: user }), {
        status: 200,
        headers: new Headers({ 'X-CSRF-TOKEN': 'csrf-rotated' }),
      })
    }
    throw new Error(`Unexpected url ${url}`)
  })

  assert.equal((await login('test@example.com', 'password123')).email, 'test@example.com')
  assert.equal(await getCsrfToken(), 'csrf-rotated')
})

test('login rejects with AuthApiError carrying code and details from the error envelope', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    if (url === '/api/v1/auth/csrf') {
      return csrfResponse('csrf-123')
    }
    return Response.json(
      { error: { code: 'INVALID_CREDENTIALS', message: 'Email hoặc mật khẩu không chính xác.' } },
      { status: 401 },
    )
  })

  await assert.rejects(login('test@example.com', 'wrong'), error => {
    assert.ok(error instanceof AuthApiError)
    assert.equal(error.status, 401)
    assert.equal(error.code, 'INVALID_CREDENTIALS')
    return true
  })
})

test('register exposes field details of a VALIDATION_ERROR', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    if (url === '/api/v1/auth/csrf') {
      return csrfResponse('csrf-123')
    }
    return Response.json({
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Dữ liệu không hợp lệ.',
        details: [{ field: 'email', issue: 'invalid_email', message: 'Email không đúng định dạng.' }],
      },
    }, { status: 400 })
  })

  await assert.rejects(register('x', 'password123'), error => {
    assert.equal(error.code, 'VALIDATION_ERROR')
    assert.equal(error.fieldErrors[0].field, 'email')
    assert.equal(error.fieldErrors[0].issue, 'invalid_email')
    return true
  })
})

test('getCurrentUser reads /api/v1/me and unwraps data', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/me')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ data: user })
  })

  const current = await getCurrentUser()
  assert.equal(current.id, 'user-uuid-1')
  assert.equal(current.email, 'test@example.com')
})

test('getCurrentUser returns null when unauthenticated (401)', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    assert.equal(url, '/api/v1/me')
    return Response.json({ error: { code: 'UNAUTHORIZED', message: 'Bạn cần đăng nhập.' } }, { status: 401 })
  })

  assert.equal(await getCurrentUser(), null)
})

test('logout posts to /api/v1/auth/logout with CSRF and clears the cache', async t => {
  let logoutCalled = false
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return csrfResponse('csrf-logout')
    }
    if (url === '/api/v1/auth/logout') {
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'same-origin')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-logout')
      logoutCalled = true
      return new Response(null, { status: 204 })
    }
    throw new Error(`Unexpected url ${url}`)
  })

  assert.equal(await logout(), true)
  assert.equal(logoutCalled, true)
})


test('CSRF warmup and submit share one in-flight token request and bypass browser caches', async t => {
  let finish, count = 0
  t.mock.method(globalThis, 'fetch', async (_, options) => {
    count++
    assert.equal(options.cache, 'no-store')
    assert.equal(options.headers['Cache-Control'], 'no-cache, no-store')
    return new Promise(resolve => { finish = () => resolve(csrfResponse('shared')) })
  })
  const warmup = getCsrfToken()
  const submit = getCsrfToken()
  assert.equal(count, 1)
  finish()
  assert.deepEqual(await Promise.all([warmup, submit]), ['shared', 'shared'])
})

test('login refreshes CSRF and retries exactly once, preserving uppercase error contract', async t => {
  let tokens = 0, attempts = 0
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return csrfResponse(++tokens === 1 ? 'stale' : 'fresh')
    attempts++
    assert.equal(options.headers['X-CSRF-TOKEN'], attempts === 1 ? 'stale' : 'fresh')
    if (attempts === 1) return Response.json({ error: { code: 'CSRF_INVALID' } }, { status: 403 })
    return Response.json({ data: user })
  })
  assert.deepEqual(await login('test@example.com', 'password123'), user)
  assert.equal(attempts, 2)
  assert.equal(tokens, 2)
})

test('a second CSRF failure is surfaced without an infinite retry', async t => {
  let attempts = 0
  t.mock.method(globalThis, 'fetch', async url => {
    if (url.endsWith('/csrf')) return csrfResponse('token')
    attempts++
    return Response.json({ error: { code: 'CSRF_INVALID' } }, { status: 403 })
  })
  await assert.rejects(login('test@example.com', 'password123'), error => error.code === 'CSRF_INVALID')
  assert.equal(attempts, 2)
})

test('unrelated 403 and invalid credentials are not retried', async t => {
  for (const [status, code] of [[403, 'ACCESS_DENIED'], [401, 'INVALID_CREDENTIALS']]) {
    clearCsrfTokenCache()
    let attempts = 0
    t.mock.method(globalThis, 'fetch', async url => {
      if (url.endsWith('/csrf')) return csrfResponse('token')
      attempts++
      return Response.json({ error: { code } }, { status })
    })
    await assert.rejects(login('test@example.com', 'wrong'), error => error.code === code)
    assert.equal(attempts, 1)
  }
})

test('cookie fallback handles CSRF endpoint failure and encoded token', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 502 }))
  mockDocument(t, 'other=1; XSRF-TOKEN=cookie%2Fvalue')
  assert.equal(await getCsrfToken(), 'cookie/value')
})

test('malformed cookie and malformed response produce a CSRF error rather than an undefined header', async t => {
  mockDocument(t, 'XSRF-TOKEN=%E0%A4%A')
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: {} }))
  await assert.rejects(getCsrfToken(), error => error instanceof AuthApiError && error.code === 'CSRF_ERROR')
})

test('old in-flight warmup cannot overwrite token from a new session', async t => {
  let finishOld, count = 0
  t.mock.method(globalThis, 'fetch', async () => {
    count++
    if (count === 1) return new Promise(resolve => { finishOld = () => resolve(csrfResponse('old-session')) })
    return csrfResponse('new-session')
  })
  const old = getCsrfToken()
  assert.equal(await getCsrfToken(true), 'new-session')
  finishOld()
  await old
  assert.equal(await getCsrfToken(), 'new-session')
})
