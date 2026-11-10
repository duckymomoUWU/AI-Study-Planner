const BASE = '/api/v1'

export class AuthApiError extends Error {
  constructor(status, code, message, fieldErrors = []) {
    super(message || 'Lỗi xác thực')
    this.name = 'AuthApiError'
    this.status = status
    this.code = code
    // Items look like { field, issue, message } (the API's error.details).
    this.fieldErrors = fieldErrors
  }
}

let cachedCsrfToken = null
let csrfRequest = null
let csrfGeneration = 0

export function clearCsrfTokenCache() {
  cachedCsrfToken = null
  csrfRequest = null
  csrfGeneration += 1
}

function errorFrom(response, payload, fallbackCode) {
  const error = payload?.error
  return new AuthApiError(
    response.status,
    error?.code || fallbackCode,
    error?.message || `Yêu cầu thất bại (${response.status})`,
    error?.details || [],
  )
}

function getCookieToken() {
  if (typeof document === 'undefined') return null
  const match = document.cookie?.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  if (!match) return null
  try { return decodeURIComponent(match[1]) || null } catch { return null }
}

export async function getCsrfToken(forceRefresh = false) {
  if (forceRefresh) clearCsrfTokenCache()
  if (cachedCsrfToken) return cachedCsrfToken
  if (csrfRequest) return csrfRequest
  const generation = csrfGeneration
  const pending = (async () => {
    let token
    try {
      const response = await fetch(`${BASE}/auth/csrf`, {
        method: 'GET', credentials: 'same-origin', cache: 'no-store',
        headers: { Accept: 'application/json', 'Cache-Control': 'no-cache, no-store' },
      })
      if (!response.ok) throw new AuthApiError(response.status, 'CSRF_ERROR', 'Không thể lấy CSRF token')
      const payload = await response.json()
      token = payload?.data?.token
      if (typeof token !== 'string' || !token.trim()) throw new AuthApiError(502, 'CSRF_ERROR', 'Phản hồi CSRF không hợp lệ')
    } catch (error) {
      token = getCookieToken()
      if (!token) {
        if (error instanceof AuthApiError) throw error
        throw new AuthApiError(503, 'CSRF_ERROR', 'Không thể lấy CSRF token')
      }
    }
    if (generation === csrfGeneration) cachedCsrfToken = token
    return token
  })()
  csrfRequest = pending
  try { return await pending }
  finally { if (csrfRequest === pending) csrfRequest = null }
}

/** Sends a write request with the CSRF header and returns the envelope's `data` (null for 204). */
async function requestWithCsrf(url, options = {}, isRetry = false) {
  const token = await getCsrfToken()
  const headers = {
    'Content-Type': 'application/json',
    'X-CSRF-TOKEN': token,
    Accept: 'application/json',
    ...(options.headers || {}),
  }

  const response = await fetch(url, {
    ...options,
    credentials: 'same-origin',
    headers,
  })

  const newCsrf = response.headers?.get ? response.headers.get('X-CSRF-TOKEN') : null
  if (newCsrf) {
    clearCsrfTokenCache()
    cachedCsrfToken = newCsrf
  }

  if (response.status === 204) {
    return null
  }

  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    // The CSRF filter rejected this request before its business action ran: retry only once.
    if (!isRetry && response.status === 403 && payload?.error?.code === 'CSRF_INVALID') {
      await getCsrfToken(true)
      return requestWithCsrf(url, options, true)
    }
    throw errorFrom(response, payload, 'auth_error')
  }

  return payload?.data ?? null
}

export async function register(email, password) {
  return requestWithCsrf(`${BASE}/auth/register`, {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function login(email, password) {
  return requestWithCsrf(`${BASE}/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function getCurrentUser() {
  const response = await fetch(`${BASE}/me`, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })

  if (response.status === 401) {
    return null
  }

  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw errorFrom(response, payload, 'me_error')
  }

  const payload = await response.json()
  return payload?.data ?? null
}

export async function logout() {
  await requestWithCsrf(`${BASE}/auth/logout`, {
    method: 'POST',
  })
  clearCsrfTokenCache()
  return true
}
