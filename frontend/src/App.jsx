import { useState } from 'react'
import LoginForm from './components/LoginForm.jsx'
import Dashboard from './components/Dashboard.jsx'

const TOKEN_KEY = 'travel-agent-jwt'

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [notice, setNotice] = useState(null)

  function handleLogin(newToken) {
    localStorage.setItem(TOKEN_KEY, newToken)
    setToken(newToken)
    setNotice(null)
  }

  function handleLogout(message) {
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setNotice(message ?? null)
  }

  if (token) {
    return <Dashboard token={token} onLogout={handleLogout} />
  }
  return <LoginForm onLogin={handleLogin} notice={notice} />
}
