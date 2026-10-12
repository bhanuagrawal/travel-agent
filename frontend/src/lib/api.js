const TOKEN_KEY = 'travel-agent-jwt'
const REFRESH_KEY = 'travel-agent-jwt-refresh'

export { TOKEN_KEY, REFRESH_KEY }

export async function login(username, password) {
  let res
  try {
    res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
  } catch {
    throw new Error('Cannot reach the API. Is Spring Boot running on :8080?')
  }

  if (res.status === 401) {
    throw new Error('Invalid username or password')
  }
  if (res.status === 404) {
    throw new Error(
      'No POST /api/auth/login on the backend yet — wire up Spring Security first'
    )
  }
  if (!res.ok) {
    throw new Error(await extractError(res, `Login failed (${res.status})`))
  }

  const data = await res.json()
  if (!data.token) {
    throw new Error('Response had no token field')
  }
  return { token: data.token, refreshToken: data.refreshToken ?? null }
}

export async function serverLogout(token, refreshToken) {
  await fetch('/api/auth/logout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ refreshToken }),
  })
}

// Exchange the stored refresh token for a fresh pair (rotation: old one dies on use).
// Returns the new access token, or null if the session is unrecoverable.
export async function refreshAccessToken() {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) return null

  let res
  try {
    res = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
  } catch {
    return null
  }
  if (!res.ok) return null

  const data = await res.json()
  if (!data.token || !data.refreshToken) return null

  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(REFRESH_KEY, data.refreshToken)
  return data.token
}

export async function get(path, token, { onTokenRefreshed, onUnauthorized } = {}) {
  let res = await sendWithToken(path, token)

  if (res.status === 401) {
    const fresh = await refreshAccessToken()
    if (fresh) {
      onTokenRefreshed?.(fresh)
      res = await sendWithToken(path, fresh)   // one silent retry with the new token
    } else {
      onUnauthorized?.()                        // refresh dead → real logout
    }
  }

  const text = await res.text()
  let body = text
  try {
    body = JSON.parse(text)
  } catch {
    return { status: res.status, ok: res.ok, body }
  }
  return { status: res.status, ok: res.ok, body }
}

async function sendWithToken(path, token) {
  try {
    return await fetch(path, {
      headers: { Authorization: `Bearer ${token}` },
    })
  } catch {
    return new Response('Network error — API unreachable', { status: 0 })
  }
}

async function extractError(res, fallback) {
  try {
    const data = await res.json()
    return data.error || data.message || fallback
  } catch {
    return fallback
  }
}
