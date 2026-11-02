import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, getSession, hasSessionIdentity, normalizeSession, saveSession, subscribeSession } from '../services/session'
import { login } from '../services/auth'
import { api } from '../services/api'
import { sessionFor, tokenFor } from './fixtures/session'

beforeEach(() => { localStorage.clear(); sessionStorage.clear(); vi.restoreAllMocks() })

describe('identidad de sesión según JWT existente', () => {
  it.each([true, false])('rehidrata userId desde JWT y conserva recordarme=%s', remember => {
    const store = remember ? localStorage : sessionStorage
    store.setItem('football-market-session', JSON.stringify({ ...sessionFor(42), userId: 999 }))
    expect(getSession().userId).toBe(42)
    saveSession(getSession(), remember)
    expect(store.getItem('football-market-session')).not.toBeNull()
    expect((remember ? sessionStorage : localStorage).getItem('football-market-session')).toBeNull()
  })
  it('login conserva la identidad emitida por el backend', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { accessToken: tokenFor({ userId: 42, sub: 'José' }), expiresInSeconds: 3600 } })
    expect((await login('José', 'password', false)).userId).toBe(42)
    expect(getSession().userId).toBe(42)
  })
  it.each([undefined, null, '42', -1, 0, 1.5, 9007199254740992])('no acepta identidad ausente o inválida %s', userId => {
    const session = { ...sessionFor(1), token: tokenFor({ userId }), userId: 42 }
    expect(normalizeSession(session).userId).toBeNull()
    expect(hasSessionIdentity(session)).toBe(false)
  })
  it('un token ilegible no habilita consultas personales por username', () => {
    expect(hasSessionIdentity({ ...sessionFor(1), token: 'ilegible', userId: 42 })).toBe(false)
  })
  it('exp del JWT limita la expiración guardada', () => {
    saveSession({ ...sessionFor(1), token: tokenFor({ exp: Math.floor(Date.now() / 1000) - 1 }) }, true)
    expect(getSession()).toBeNull()
    expect(localStorage.getItem('football-market-session')).toBeNull()
  })
  it('descarta almacenamiento corrupto y notifica logout/cambio de usuario', () => {
    localStorage.setItem('football-market-session', '{')
    expect(getSession()).toBeNull()
    const changed = vi.fn()
    const unsubscribe = subscribeSession(changed)
    saveSession(sessionFor(1), true)
    saveSession(sessionFor(2), false)
    clearSession()
    expect(changed).toHaveBeenCalledTimes(3)
    expect(getSession()).toBeNull()
    expect(localStorage.getItem('football-market-session')).toBeNull()
    expect(sessionStorage.getItem('football-market-session')).toBeNull()
    unsubscribe()
  })
})
