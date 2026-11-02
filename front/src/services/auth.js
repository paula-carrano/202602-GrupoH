import { api } from './api'
import { normalizeSession, saveSession } from './session'

export const login = async (username, password, remember = true) => {
  const { data } = await api.post('/auth/login', { username, password })
  const session = normalizeSession({
    token: data.accessToken,
    username,
    expiresAt: Date.now() + data.expiresInSeconds * 1000,
  })
  saveSession(session, remember)
  return session
}

export const register = async user => {
  const { data } = await api.post('/auth/register', user)
  return data
}
