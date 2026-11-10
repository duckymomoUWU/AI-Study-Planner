import { test, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { register, login, getCurrentUser, logout, getCsrfToken, clearCsrfTokenCache, AuthApiError } from './auth.js'

beforeEach(() => {
  clearCsrfTokenCache()
})

test('getCsrfToken requests /api/v1/auth/csrf and caches token', async t => {
  let callCount = 0
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/auth/csrf')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    callCount++
    return Response.json({
      data: {
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'mock-csrf-token-123',
      },
    })
  })

  const token1 = await getCsrfToken()
  assert.equal(token1, 'mock-csrf-token-123')

  const token2 = await getCsrfToken()
  assert.equal(token2, 'mock-csrf-token-123')
  assert.equal(callCount, 1, 'Token should be cached after first request')
})

test('register fetches CSRF and sends POST with X-CSRF-TOKEN and credentials', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'csrf-abc' } })
    }
    if (url === '/api/v1/auth/register') {
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'same-origin')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-abc')
      const body = JSON.parse(options.body)
      assert.equal(body.email, 'test@example.com')
      assert.equal(body.password, 'password123')
      return Response.json({ data: { id: 'user-uuid-1', email: 'test@example.com' } }, { status: 201 })
    }
    throw new Error(`Unexpected url ${url}`)
  })

  const user = await register('test@example.com', 'password123')
  assert.equal(user.id, 'user-uuid-1')
  assert.equal(user.email, 'test@example.com')
})

test('login sends credentials and updates cached CSRF token if returned in header', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'csrf-initial' } })
    }
    if (url === '/api/v1/auth/login') {
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'same-origin')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-initial')
      const headers = new Headers({ 'X-CSRF-TOKEN': 'csrf-rotated' })
      return new Response(JSON.stringify({ data: { id: 'user-uuid-1', email: 'test@example.com' } }), {
        status: 200,
        headers,
      })
    }
    throw new Error(`Unexpected url ${url}`)
  })

  const user = await login('test@example.com', 'password123')
  assert.equal(user.email, 'test@example.com')

  // Next CSRF request should return the rotated token from cache
  assert.equal(await getCsrfToken(), 'csrf-rotated')
})

test('login rejects with AuthApiError on invalid credentials', async t => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'csrf-123' } })
    }
    return Response.json(
      { error: { code: 'INVALID_CREDENTIALS', message: 'Mật khẩu không chính xác.' } },
      { status: 401 }
    )
  })

  await assert.rejects(login('test@example.com', 'wrong'), error => {
    assert.ok(error instanceof AuthApiError)
    assert.equal(error.status, 401)
    assert.equal(error.code, 'invalid_credentials')
    return true
  })
})

test('login rejects with USER_NOT_FOUND when account does not exist', async t => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'csrf-123' } })
    }
    return Response.json(
      { error: { code: 'USER_NOT_FOUND', message: 'Tài khoản không tồn tại.' } },
      { status: 401 }
    )
  })

  await assert.rejects(login('nonexistent@example.com', 'pass'), error => {
    assert.ok(error instanceof AuthApiError)
    assert.equal(error.status, 401)
    assert.equal(error.code, 'user_not_found')
    assert.equal(error.message, 'Tài khoản không tồn tại.')
    return true
  })
})

test('getCurrentUser returns user object when authenticated', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/me')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ data: { id: 'user-uuid-1', email: 'test@example.com' } })
  })

  const user = await getCurrentUser()
  assert.equal(user.id, 'user-uuid-1')
  assert.equal(user.email, 'test@example.com')
})

test('getCurrentUser returns null when unauthenticated (401)', async t => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    assert.equal(url, '/api/v1/me')
    return Response.json({ error: { code: 'UNAUTHORIZED' } }, { status: 401 })
  })

  const user = await getCurrentUser()
  assert.equal(user, null)
})

test('logout calls /api/v1/auth/logout with CSRF and clears cache', async t => {
  let logoutCalled = false
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'csrf-logout' } })
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

  const result = await logout()
  assert.equal(result, true)
  assert.equal(logoutCalled, true)
})

test('requestWithCsrf automatically retries on 403 CSRF_INVALID with fresh token', async t => {
  let csrfFetchCount = 0
  let loginAttemptCount = 0

  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      csrfFetchCount++
      const token = csrfFetchCount === 1 ? 'stale-token' : 'fresh-token'
      return Response.json({ data: { token } })
    }
    if (url === '/api/v1/auth/login') {
      loginAttemptCount++
      if (loginAttemptCount === 1) {
        assert.equal(options.headers['X-CSRF-TOKEN'], 'stale-token')
        return Response.json(
          { error: { code: 'CSRF_INVALID', message: 'Yêu cầu không hợp lệ hoặc phiên đã hết hạn.' } },
          { status: 403 }
        )
      }
      assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh-token')
      return Response.json({ data: { id: 'user-retry-1', email: 'retry@example.com' } }, { status: 200 })
    }
    throw new Error(`Unexpected url ${url}`)
  })

  const user = await login('retry@example.com', 'password123')
  assert.equal(user.email, 'retry@example.com')
  assert.equal(csrfFetchCount, 2, 'Should have fetched fresh CSRF token')
  assert.equal(loginAttemptCount, 2, 'Should have retried login request')
})

test('getCsrfToken falls back to document.cookie XSRF-TOKEN when fetch fails', async t => {
  t.mock.method(globalThis, 'fetch', async () => {
    return new Response(null, { status: 502 })
  })

  // Mock document.cookie
  const originalDocument = globalThis.document
  globalThis.document = {
    cookie: 'other=123; XSRF-TOKEN=cookie-csrf-val; JSESSIONID=abc',
  }

  try {
    const token = await getCsrfToken()
    assert.equal(token, 'cookie-csrf-val')
  } finally {
    globalThis.document = originalDocument
  }
})

