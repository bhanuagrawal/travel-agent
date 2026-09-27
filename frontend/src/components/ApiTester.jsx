import { useState } from 'react'
import { get } from '../lib/api.js'

export default function ApiTester({ token, onUnauthorized }) {
  const [path, setPath] = useState('/api/v1/health')
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)

  async function sendRequest() {
    setBusy(true)
    setResult(null)
    const res = await get(path, token)
    setResult(res)
    if (res.status === 401) {
      onUnauthorized(
        'API rejected your token (401) — logged out. Log in again to get a fresh JWT.'
      )
      return
    }
    setBusy(false)
  }

  return (
    <div className="panel">
      <div className="panel-title">
        <h2>Protected API call</h2>
        <span className="chip chip-blue">Authorization: Bearer</span>
      </div>

      <div className="tester-row">
        <span className="method">GET</span>
        <input
          type="text"
          value={path}
          onChange={(e) => setPath(e.target.value)}
          placeholder="/api/v1/health"
        />
        <button onClick={sendRequest} disabled={busy || !path.startsWith('/')}>
          {busy ? 'Sending…' : 'Send'}
        </button>
      </div>

      {result && (
        <div className="result">
          <span
            className={`chip ${
              result.ok
                ? 'chip-green'
                : result.status >= 500 || result.status === 0
                  ? 'chip-red'
                  : 'chip-amber'
            }`}
          >
            {result.status || 'ERR'}
          </span>
          <pre>{prettyBody(result.body)}</pre>
        </div>
      )}

      <p className="muted">
        Every request leaves this page with{' '}
        <code>Authorization: Bearer &lt;your-token&gt;</code> in the header —
        that is the whole JWT flow. A 401 means the backend rejected it.
      </p>
    </div>
  )
}

function prettyBody(body) {
  if (typeof body === 'string') return body
  return JSON.stringify(body, null, 2)
}
