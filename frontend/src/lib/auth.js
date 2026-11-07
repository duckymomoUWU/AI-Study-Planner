export class AuthApiError extends Error {
  constructor(status, code, message, fieldErrors = []) {
    super(message || 'Lỗi xác thực')
    this.name = 'AuthApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

let cachedCsrfToken = null

export function clearCsrfTokenCache() {
  cachedCsrfToken = null
}

export async function getCsrfToken() {
  if (cachedCsrfToken) {
    return cachedCsrfToken
  }
  const response = await fetch('/api/auth/csrf', {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new AuthApiError(response.status, 'csrf_error', 'Không thể lấy CSRF token')
  }
  const data = await response.json()
  cachedCsrfToken = data.token
  return cachedCsrfToken
}

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
    const code = payload?.code || 'auth_error'
    const message = payload?.message || `Yêu cầu thất bại (${response.status})`
    const fieldErrors = payload?.fieldErrors || []
    throw new AuthApiError(response.status, code, message, fieldErrors)
  }

  return payload
}

export async function register(email, password) {
  return requestWithCsrf('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function login(email, password) {
  return requestWithCsrf('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function getCurrentUser() {
  const response = await fetch('/api/auth/me', {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })

  if (response.status === 401) {
    return null
  }

  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new AuthApiError(
      response.status,
      payload?.code || 'me_error',
      payload?.message || 'Không thể kiểm tra phiên đăng nhập'
    )
  }

  return await response.json()
}

export async function logout() {
  await requestWithCsrf('/api/auth/logout', {
    method: 'POST',
  })
  clearCsrfTokenCache()
  return true
}
