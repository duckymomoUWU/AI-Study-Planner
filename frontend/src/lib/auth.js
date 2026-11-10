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

function getCookieToken() {
  if (typeof document === 'undefined' || !document.cookie) return null
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

export async function getCsrfToken(forceRefresh = false) {
  if (cachedCsrfToken && !forceRefresh) {
    return cachedCsrfToken
  }

  try {
    const response = await fetch('/api/v1/auth/csrf', {
      method: 'GET',
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
        'Cache-Control': 'no-cache, no-store',
      },
      cache: 'no-store',
    })

    if (!response.ok) {
      throw new AuthApiError(response.status, 'csrf_error', 'Không thể lấy CSRF token')
    }

    const data = await response.json()
    const token = data.data?.token || data.token
    if (token) {
      cachedCsrfToken = token
      return cachedCsrfToken
    }
    throw new AuthApiError(500, 'csrf_error', 'Không thể lấy CSRF token')
  } catch (err) {
    // If fetching failed, check document.cookie as fallback
    const cookieToken = getCookieToken()
    if (cookieToken) {
      cachedCsrfToken = cookieToken
      return cachedCsrfToken
    }
    if (err instanceof AuthApiError) {
      throw err
    }
    throw new AuthApiError(500, 'csrf_error', 'Không thể lấy CSRF token')
  }
}

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
    cachedCsrfToken = newCsrf
  }

  if (response.status === 204) {
    return null
  }

  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    const errorBody = payload?.error || payload || {}
    const rawCode = errorBody.code || 'auth_error'
    const code = rawCode.toLowerCase()

    // Auto-retry once on 403 CSRF error / session timeout with fresh token
    if (!isRetry && (response.status === 403 || code === 'csrf_invalid' || code === 'forbidden_or_csrf')) {
      clearCsrfTokenCache()
      await getCsrfToken(true).catch(() => null)
      return requestWithCsrf(url, options, true)
    }

    const message = errorBody.message || `Yêu cầu thất bại (${response.status})`
    const fieldErrors = errorBody.details || errorBody.fieldErrors || []
    throw new AuthApiError(response.status, code, message, fieldErrors)
  }

  return payload?.data !== undefined ? payload.data : payload
}

export async function register(email, password) {
  return requestWithCsrf('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function login(email, password) {
  return requestWithCsrf('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function getCurrentUser() {
  const response = await fetch('/api/v1/me', {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })

  if (response.status === 401) {
    return null
  }

  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    const errorBody = payload?.error || payload || {}
    const rawCode = errorBody.code || 'me_error'
    throw new AuthApiError(
      response.status,
      rawCode.toLowerCase(),
      errorBody.message || 'Không thể kiểm tra phiên đăng nhập'
    )
  }

  return payload?.data !== undefined ? payload.data : payload
}

export async function logout() {
  await requestWithCsrf('/api/v1/auth/logout', {
    method: 'POST',
  })
  clearCsrfTokenCache()
  return true
}
