import { useState } from 'react'
import LoginForm from './components/LoginForm.jsx'
import Dashboard from './components/Dashboard.jsx'
import { serverLogout, TOKEN_KEY, REFRESH_KEY } from './lib/api.js'

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [notice, setNotice] = useState(null)

  function handleLogin({ token, refreshToken }) {
    localStorage.setItem(TOKEN_KEY, token)
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
    setToken(token)
    setNotice(null)
  }

  async function handleLogout(message) {
    const refreshToken = localStorage.getItem(REFRESH_KEY)
    if (token && refreshToken) {
      await serverLogout(token, refreshToken).catch(() => {})   // best effort — clear locally regardless
    }
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    setToken(null)
    setNotice(message ?? null)
  }

  function handleTokenRefreshed(newToken) {
    setToken(newToken)   // TokenPanel + countdown re-render with the fresh expiry
    setNotice('Session extended — the backend issued a fresh token')
  }

  if (token) {
    return (
      <Dashboard
        token={token}
        onLogout={handleLogout}
        onTokenRefreshed={handleTokenRefreshed}
      />
    )
  }
  return <LoginForm onLogin={handleLogin} notice={notice} />
}
