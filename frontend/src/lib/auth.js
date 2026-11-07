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

export function clearCsrfTokenCache() {
  cachedCsrfToken = null
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

export async function getCsrfToken() {
  if (cachedCsrfToken) {
    return cachedCsrfToken
  }
  const response = await fetch(`${BASE}/auth/csrf`, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new AuthApiError(response.status, 'csrf_error', 'Không thể lấy CSRF token')
  }
  const payload = await response.json()
  cachedCsrfToken = payload?.data?.token
  return cachedCsrfToken
}

/** Sends a write request with the CSRF header and returns the envelope's `data` (null for 204). */
async function requestWithCsrf(url, options = {}) {
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
    cachedCsrfToken = newCsrf
  }

  if (response.status === 204) {
    return null
  }

  const payload = await response.json().catch(() => null)

  if (!response.ok) {
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
