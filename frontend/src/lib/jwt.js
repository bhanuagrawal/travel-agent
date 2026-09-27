function base64UrlDecode(segment) {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(bytes))
}

export function decodeJwt(token) {
  try {
    const [headerSeg, payloadSeg] = token.split('.')
    if (!headerSeg || !payloadSeg) return null
    return {
      header: base64UrlDecode(headerSeg),
      payload: base64UrlDecode(payloadSeg),
    }
  } catch {
    return null
  }
}

export function formatClaim(name, value) {
  if ((name === 'exp' || name === 'iat') && typeof value === 'number') {
    return new Date(value * 1000).toLocaleString()
  }
  return typeof value === 'string' ? value : JSON.stringify(value)
}
