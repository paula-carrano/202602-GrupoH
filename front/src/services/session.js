const KEY = 'football-market-session'

export function getSession() {
  try {
    const raw = localStorage.getItem(KEY) || sessionStorage.getItem(KEY)
    const session = raw ? JSON.parse(raw) : null
    if (session?.token && session?.expiresAt > Date.now()) return session
  } catch { /* Invalid saved data is discarded below. */ }
  clearSession()
  return null
}

export function saveSession(session, remember) {
  clearSession()
  const store = remember ? localStorage : sessionStorage
  store.setItem(KEY, JSON.stringify(session))
}

export function clearSession() {
  localStorage.removeItem(KEY)
  sessionStorage.removeItem(KEY)
}
