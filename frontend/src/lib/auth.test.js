import { test, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { register, login, getCurrentUser, logout, getCsrfToken, clearCsrfTokenCache, AuthApiError } from './auth.js'

beforeEach(() => {
  clearCsrfTokenCache()
})

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
