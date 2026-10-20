import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, getApiError } from '../services/api'
import { getPlayer, getPlayers } from '../services/players'
import { login, register } from '../services/auth'
import { saveSession } from '../services/session'
import { sessionFor, tokenFor } from './fixtures/session'
import { act, renderHook } from '@testing-library/react'
import { useApiRequest } from '../hooks/useApiRequest'

const originalAdapter = api.defaults.adapter
beforeEach(() => { localStorage.clear(); sessionStorage.clear() })
afterEach(() => { api.defaults.adapter = originalAdapter; vi.restoreAllMocks() })

describe('contratos AuthController, PlayerController y cliente Axios real', () => {
  it('conserva base, timeout, Bearer, rutas y señal de aborto', async () => {
    const session = sessionFor(1)
    saveSession(session, true)
    const adapter = vi.fn(async config => ({ data: [], status: 200, headers: {}, config }))
    api.defaults.adapter = adapter
    const controller = new AbortController()
    await getPlayers({ signal: controller.signal })
    await getPlayer(7, { signal: controller.signal })
    expect(adapter.mock.calls[0][0]).toMatchObject({ baseURL: '/api/v1', timeout: 12000, url: '/players', signal: controller.signal })
    expect(adapter.mock.calls[0][0].headers.Authorization).toBe(`Bearer ${session.token}`)
    expect(adapter.mock.calls[1][0].url).toBe('/players/7')
    controller.abort()
    await expect(getPlayers({ signal: controller.signal })).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(adapter).toHaveBeenCalledTimes(2)
  })
  it('envía los cuerpos actuales de registro y login sin credenciales previas', async () => {
    const adapter = vi.fn(async config => ({ data: { accessToken: tokenFor(), expiresInSeconds: 3600 }, status: 200, headers: {}, config }))
    api.defaults.adapter = adapter
    await register({ username: 'usuario', email: 'u@example.com', password: 'secret' })
    await login('usuario', 'secret')
    expect(adapter.mock.calls.map(([config]) => config.url)).toEqual(['/auth/register', '/auth/login'])
    expect(JSON.parse(adapter.mock.calls[1][0].data)).toEqual({ username: 'usuario', password: 'secret' })
    expect(adapter.mock.calls[1][0].headers.Authorization).toBeUndefined()
  })
  it.each(['ERR_NETWORK', 'ECONNABORTED'])('propaga %s sin reintentar automáticamente', async code => {
    const adapter = vi.fn().mockRejectedValue({ code })
    api.defaults.adapter = adapter
    await expect(getPlayers()).rejects.toMatchObject({ code })
    expect(adapter).toHaveBeenCalledTimes(1)
  })
})

describe('normalización según ErrorCode y ProblemDetail del backend', () => {
  it.each([
    ['INVALID_CREDENTIALS', 'Credenciales inválidas'],
    ['PLAYER_NOT_FOUND', 'No se encontró el jugador.'],
    ['EMAIL_ALREADY_EXISTS', 'El email ya está registrado.'],
    ['VALIDATION_FAILED', 'Revisá los datos ingresados.'],
  ])('traduce %s sin exponer detail', (errorCode, message) => {
    expect(getApiError({ response: { data: { errorCode, detail: 'java.stacktrace secret' } } })).toBe(message)
  })
  it.each(['DESCONOCIDO', 'toString', undefined])('usa fallback seguro para %s', errorCode => {
    expect(getApiError({ response: { data: { errorCode, detail: 'secret' } } })).toBe('Ocurrió un error inesperado. Por favor, intentá nuevamente más tarde.')
  })
  it.each([[401, 'La sesión no es válida.'], [403, 'No tenés permiso']])('maneja %s sin ProblemDetail', (status, message) => {
    expect(getApiError({ response: { status } })).toContain(message)
  })
  it('distingue red, timeout y cancelación', () => {
    expect(getApiError({})).toContain('No se pudo conectar')
    expect(getApiError({ code: 'ECONNABORTED' })).toContain('tardó demasiado')
    expect(getApiError({ code: 'ERR_CANCELED' })).toBe('')
    expect(getApiError({ name: 'AbortError' })).toBe('')
  })
  it('el hook de formularios no muestra cancelación como error ordinario', async () => {
    const { result } = renderHook(useApiRequest)
    await act(async () => result.current.run(() => Promise.reject({ code: 'ERR_CANCELED' })))
    expect(result.current.error).toBe('')
    expect(result.current.loading).toBe(false)
  })
})
