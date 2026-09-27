import { useState } from 'react'
import { login } from '../lib/api.js'

export default function LoginForm({ onLogin, notice }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const token = await login(username, password)
      onLogin(token)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="page">
      <section className="card">
        <header className="card-header">
          <div className="logo">✈</div>
          <h1>Eco-Flight Travel Agent</h1>
          <p className="subtitle">Sign in to plan your journey</p>
        </header>

        {notice && <div className="banner banner-notice">{notice}</div>}
        {error && <div className="banner banner-error">{error}</div>}

        <form onSubmit={handleSubmit}>
          <label htmlFor="username">Username</label>
          <input
            id="username"
            type="text"
            value={username}
            autoComplete="username"
            onChange={(e) => setUsername(e.target.value)}
            placeholder="user"
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
          />

          <button type="submit" disabled={busy || !username || !password}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <footer className="contract">
          <strong>Backend contract</strong>
          <code>POST /api/auth/login</code>
          <span>
            {'{ "username", "password" }'} → {'{ "token": "<jwt>" }'} · 401 on
            bad credentials
          </span>
        </footer>
      </section>
    </main>
  )
}
