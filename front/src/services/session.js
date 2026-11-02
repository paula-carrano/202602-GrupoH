const KEY = 'football-market-session'
const listeners = new Set()

const notify = () => listeners.forEach(listener => listener())

// Identificación de presentación, NO validación de firma ni autorización.
export const readTokenClaims = token => {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null

    const payload = parts[1]
      .replaceAll('-', '+')
      .replaceAll('_', '/')

    const bytes = Uint8Array.from(
      atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, '=')),
      char => char.codePointAt(0)
    )

    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

export const normalizeSession = session => {
  if (!session) return null

  const claims = readTokenClaims(session.token)

  const userId =
    Number.isSafeInteger(claims?.userId) && claims.userId > 0
      ? claims.userId
      : null

  const expiresAt = Number.isFinite(claims?.exp)
    ? Math.min(session.expiresAt, claims.exp * 1000)
    : session.expiresAt

  return { ...session, userId, expiresAt }
}

export const hasSessionIdentity = session => {
  const normalized = normalizeSession(session)

  return (
    normalized?.userId != null &&
    normalized.expiresAt > Date.now()
  )
}

export const subscribeSession = listener => {
  listeners.add(listener)

  const storageChanged = event => {
    if (event.key === KEY || event.key === null) {
      listener()
    }
  }

  window.addEventListener('storage', storageChanged)

  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', storageChanged)
  }
}

const removeStoredSession = () => {
  localStorage.removeItem(KEY)
  sessionStorage.removeItem(KEY)
}

export const getSession = () => {
  try {
    const raw =
      localStorage.getItem(KEY) || sessionStorage.getItem(KEY)

    const session = raw
      ? normalizeSession(JSON.parse(raw))
      : null

    if (session?.token && session?.expiresAt > Date.now()) {
      return session
    }
  } catch {
    // Invalid saved data is discarded below.
  }

  removeStoredSession()
  return null
}

export const saveSession = (session, remember) => {
  removeStoredSession()

  const store = remember ? localStorage : sessionStorage

  store.setItem(KEY, JSON.stringify(normalizeSession(session)))

  notify()
}

export const clearSession = () => {
  removeStoredSession()
  notify()
}