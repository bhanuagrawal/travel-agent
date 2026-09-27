import { useEffect, useState } from 'react'
import { formatClaim } from '../lib/jwt.js'

const SEGMENTS = ['seg-header', 'seg-payload', 'seg-signature']

export default function TokenPanel({ token, decoded }) {
  const [copied, setCopied] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  const exp = decoded?.payload?.exp
  const remainingMs = exp ? exp * 1000 - now : null
  const expired = remainingMs !== null && remainingMs <= 0

  async function copyToken() {
    await navigator.clipboard.writeText(token)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className="panel">
      <div className="panel-title">
        <h2>Your JWT</h2>
        <div className="panel-actions">
          {remainingMs !== null && (
            <span className={`chip ${expired ? 'chip-red' : 'chip-green'}`}>
              {expired
                ? 'expired'
                : `expires in ${formatDuration(remainingMs)}`}
            </span>
          )}
          <button className="btn-ghost" onClick={copyToken}>
            {copied ? 'Copied!' : 'Copy token'}
          </button>
        </div>
      </div>

      <pre className="token-raw">
        {token.split('.').map((seg, i) => (
          <span key={i} className={SEGMENTS[i] ?? ''}>
            {seg}
            {i < 2 ? '.' : ''}
          </span>
        ))}
      </pre>

      {decoded ? (
        <div className="claims">
          <ClaimTable title="header" claims={decoded.header} />
          <ClaimTable title="payload" claims={decoded.payload} />
        </div>
      ) : (
        <p className="muted">
          Token is not a decodable JWT — is the backend really issuing JWTs?
        </p>
      )}
    </div>
  )
}

function ClaimTable({ title, claims }) {
  return (
    <div className="claims-block">
      <h3>{title}</h3>
      <table>
        <tbody>
          {Object.entries(claims).map(([key, value]) => (
            <tr key={key}>
              <td className="claim-key">{key}</td>
              <td className="claim-value">{formatClaim(key, value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function formatDuration(ms) {
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
