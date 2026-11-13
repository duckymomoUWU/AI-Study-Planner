export const ALLOWED_REDIRECT_ROUTES = ['goals', 'daily', 'overview', 'timer', 'recall', 'landing']

/**
 * Validates and resolves a safe internal route to prevent Open Redirect vulnerabilities.
 * Strips dangerous schemes (http, https, //, javascript) and validates against an internal allowlist.
 *
 * @param {string|null|undefined} rawRoute
 * @param {string} [fallback='goals']
 * @returns {string} Safe allowed route name
 */
export function getSafeRedirectRoute(rawRoute, fallback = 'goals') {
  if (!rawRoute || typeof rawRoute !== 'string') {
    return fallback
  }

  const trimmed = rawRoute.trim()

  // Strictly block absolute URLs, scheme-relative URLs, and script pseudo-protocols
  if (/^([a-z][a-z0-9+.-]*:|\/\/)/i.test(trimmed)) {
    return fallback
  }

  // Strip leading slashes, hashes, or backslashes
  const clean = trimmed.replace(/^[#/\\ ]+/, '').toLowerCase()
  const route = clean.split(/[?#/]/)[0]

  if (ALLOWED_REDIRECT_ROUTES.includes(route)) {
    return route
  }

  return fallback
}

/**
 * Extracts and sanitizes the 'redirect' query parameter from a hash or search string.
 *
 * @param {string} [searchOrHash='']
 * @param {string} [fallback='goals']
 * @returns {string} Safe allowed route name
 */
export function extractSafeRedirect(searchOrHash = '', fallback = 'goals') {
  if (!searchOrHash || typeof searchOrHash !== 'string') {
    return fallback
  }

  try {
    let qs = searchOrHash
    if (qs.includes('?')) {
      qs = qs.slice(qs.indexOf('?') + 1)
    }
    const params = new URLSearchParams(qs)
    const rawRedirect = params.get('redirect')
    return getSafeRedirectRoute(rawRedirect, fallback)
  } catch {
    return fallback
  }
}

