import { test, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { register, login, getCurrentUser, logout, getCsrfToken, clearCsrfTokenCache, AuthApiError } from './auth.js'

beforeEach(() => {
  clearCsrfTokenCache()
})

test('getCsrfToken requests /api/auth/csrf and caches token', async t => {
  let callCount = 0
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/auth/csrf')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    callCount++
    return Response.json({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'mock-csrf-token-123',
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
    if (url === '/api/auth/csrf') {
      return Response.json({ token: 'csrf-abc' })
    }
    if (url === '/api/auth/register') {
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'same-origin')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-abc')
      const body = JSON.parse(options.body)
      assert.equal(body.email, 'test@example.com')
      assert.equal(body.password, 'password123')
      return Response.json({ id: 'user-uuid-1', email: 'test@example.com' }, { status: 201 })
    }
    throw new Error(`Unexpected url ${url}`)
  })

  const user = await register('test@example.com', 'password123')
  assert.equal(user.id, 'user-uuid-1')
  assert.equal(user.email, 'test@example.com')
})

test('login sends credentials and updates cached CSRF token if returned in header', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') {
      return Response.json({ token: 'csrf-initial' })
    }
    if (url === '/api/auth/login') {
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'same-origin')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-initial')
      const headers = new Headers({ 'X-CSRF-TOKEN': 'csrf-rotated' })
      return new Response(JSON.stringify({ id: 'user-uuid-1', email: 'test@example.com' }), {
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
    if (url === '/api/auth/csrf') {
      return Response.json({ token: 'csrf-123' })
    }
    return Response.json(
      { code: 'invalid_credentials', message: 'Email hoặc mật khẩu không chính xác.' },
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

test('getCurrentUser returns user object when authenticated', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/auth/me')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ id: 'user-uuid-1', email: 'test@example.com' })
  })

  const user = await getCurrentUser()
  assert.equal(user.id, 'user-uuid-1')
  assert.equal(user.email, 'test@example.com')
})

test('getCurrentUser returns null when unauthenticated (401)', async t => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    assert.equal(url, '/api/auth/me')
    return Response.json({ code: 'unauthenticated' }, { status: 401 })
  })

  const user = await getCurrentUser()
  assert.equal(user, null)
})

test('logout calls /api/auth/logout with CSRF and clears cache', async t => {
  let logoutCalled = false
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') {
      return Response.json({ token: 'csrf-logout' })
    }
    if (url === '/api/auth/logout') {
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
