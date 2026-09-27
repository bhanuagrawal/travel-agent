import TokenPanel from './TokenPanel.jsx'
import ApiTester from './ApiTester.jsx'
import { decodeJwt } from '../lib/jwt.js'

export default function Dashboard({ token, onLogout }) {
  const decoded = decodeJwt(token)
  const username = decoded?.payload?.sub ?? 'unknown'

  return (
    <main className="page">
      <section className="card card-wide">
        <header className="dash-header">
          <div>
            <h1>Eco-Flight Travel Agent</h1>
            <p className="subtitle">
              Signed in as <strong>{username}</strong>
            </p>
          </div>
          <button className="btn-ghost" onClick={() => onLogout()}>
            Log out
          </button>
        </header>

        <TokenPanel token={token} decoded={decoded} />
        <ApiTester token={token} onUnauthorized={onLogout} />
      </section>
    </main>
  )
}
