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
  return data.token
}

export async function get(path, token) {
  let res
  try {
    res = await fetch(path, {
      headers: { Authorization: `Bearer ${token}` },
    })
  } catch {
    return { status: 0, ok: false, body: 'Network error — API unreachable' }
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

async function extractError(res, fallback) {
  try {
    const data = await res.json()
    return data.error || data.message || fallback
  } catch {
    return fallback
  }
}
