import { PlayerProfileDialog } from '../components'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, renderHook, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { App } from '../App'

import { useSessionRequest } from '../hooks/useSessionRequest'
import { api } from '../services/api'
import { clearSession, getSession, saveSession } from '../services/session'
import { sessionFor, tokenFor } from './fixtures/session'

// Catalog scenarios isolate their transport from the newly shared financial reads.
const catalogGet = () => {
  const catalog = vi.fn()
  vi.spyOn(api, 'get').mockImplementation((url, options) => {
    if (url === '/players' || /^\/players\/\d+$/.test(url)) return catalog(url, options)
    if (url.includes('/portfolio')) return Promise.resolve({ data: { creditsBalance: 0, totalCurrentValue: 0, totalInvestedCost: 0, totalPnLNominal: 0, positions: [] } })
    if (url.includes('/quotes')) return Promise.resolve({ data: options?.params?.date ? { price: 1, currency: 'CRD' } : { content: [], pageNumber: 0, totalElements: 0, totalPages: 0, last: true } })
    return Promise.resolve({ data: [] })
  })
  return catalog
}
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const player = (id, name) => ({ id, firstName: name, lastName: 'Prueba', currentTeam: 'Equipo', league: 'PL' })
const renderCatalog = () => render(<MemoryRouter initialEntries={['/players']}><App /></MemoryRouter>)
beforeEach(() => { localStorage.clear(); sessionStorage.clear(); vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise(() => {}))) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers(); localStorage.clear(); sessionStorage.clear() })

describe('aislamiento de consultas por sesión y recurso', () => {
  it.each(['success', '401'])('descarta respuesta %s de A después de cambiar a B', async outcome => {
    const pending = deferred()
    saveSession(sessionFor(1), true)
    const get = catalogGet().mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce({ data: [player(2, 'UsuarioB')] })
    renderCatalog()
    const oldSignal = get.mock.calls[0][1].signal
    act(() => saveSession(sessionFor(2), true))
    expect(oldSignal.aborted).toBe(true)
    expect(await screen.findByText('UsuarioB Prueba')).toBeInTheDocument()
    await act(async () => {
      if (outcome === 'success') pending.resolve({ data: [player(1, 'UsuarioA')] })
      else pending.reject({ response: { status: 401 } })
    })
    expect(screen.queryByText('UsuarioA Prueba')).not.toBeInTheDocument()
    expect(screen.getByText('UsuarioB Prueba')).toBeInTheDocument()
    expect(getSession().userId).toBe(2)
    expect(screen.queryByRole('button', { name: 'Iniciar sesión' })).not.toBeInTheDocument()
  })

  it('limpia selección, búsqueda y perfil en curso al cambiar usuario', async () => {
    const pending = deferred()
    const user = userEvent.setup()
    saveSession(sessionFor(1), true)
    const get = catalogGet().mockResolvedValueOnce({ data: [player(1, 'Anterior')] })
      .mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ data: [player(2, 'Nuevo')] })
    renderCatalog()
    await screen.findByText('Anterior Prueba')
    await user.type(screen.getByRole('textbox', { name: 'Buscar jugador por nombre' }), 'Anterior')
    await user.click(await screen.findByRole('button', { name: 'Ver perfil de Anterior Prueba' }))
    const signal = get.mock.calls[1][1].signal
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    act(() => saveSession(sessionFor(2), true))
    expect(signal.aborted).toBe(true)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Buscar jugador por nombre' })).toHaveValue('')
    await act(async () => pending.resolve({ data: { ...player(1, 'Anterior'), currentTeam: 'Club anterior' } }))
    expect(await screen.findByText('Nuevo Prueba')).toBeInTheDocument()
    expect(screen.queryByText('Club anterior')).not.toBeInTheDocument()
  })

  it('logout cancela consultas y descarta resolución posterior', async () => {
    const pending = deferred()
    saveSession(sessionFor(1), true)
    const get = catalogGet().mockReturnValue(pending.promise)
    renderCatalog()
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
    expect(get.mock.calls[0][1].signal.aborted).toBe(true)
    await act(async () => pending.resolve({ data: [player(1, 'Anterior')] }))
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.queryByText('Anterior Prueba')).not.toBeInTheDocument()
  })

  it('descarta detalle tardío del jugador anterior aunque el transporte no aborte', async () => {
    const old = deferred()
    const get = catalogGet().mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ data: { ...player(2, 'Nuevo'), currentTeam: 'Club nuevo' } })
    const view = render(<PlayerProfileDialog session={sessionFor(1)} player={player(1, 'Anterior')} />)
    const signal = get.mock.calls[0][1].signal
    view.rerender(<PlayerProfileDialog session={sessionFor(1)} player={player(2, 'Nuevo')} />)
    expect(signal.aborted).toBe(true)
    await screen.findByText('Club nuevo')
    await act(async () => old.resolve({ data: { ...player(1, 'Anterior'), currentTeam: 'Club anterior' } }))
    expect(screen.getByText('Club nuevo')).toBeInTheDocument()
    expect(screen.queryByText('Club anterior')).not.toBeInTheDocument()
  })

  it('un reintento invalida la generación anterior y la cancelación no es un error', async () => {
    const old = deferred()
    const request = vi.fn().mockReturnValueOnce(old.promise).mockRejectedValueOnce({ code: 'ERR_CANCELED' })
    const session = sessionFor(1)
    const { result } = renderHook(() => useSessionRequest({ session, requestKey: 'test', request }))
    const signal = request.mock.calls[0][0].signal
    act(() => result.current.retry())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(signal.aborted).toBe(true)
    await act(async () => old.resolve({ data: 'dato viejo' }))
    expect(result.current.data).toBeNull()
    expect(result.current.error).toBe('')
  })

  it('bloquea consultas personales sin identidad contractual aunque haya username', () => {
    const request = vi.fn()
    const session = { ...sessionFor(1), token: tokenFor({ userId: undefined }) }
    const { result } = renderHook(() => useSessionRequest({ session, requestKey: 'personal', personal: true, request }))
    expect(request).not.toHaveBeenCalled()
    expect(result.current.unavailable).toBe(true)
  })

  it('responde a logout en otra pestaña', async () => {
    saveSession(sessionFor(1), true)
    const pending = deferred()
    const get = catalogGet().mockReturnValue(pending.promise)
    renderCatalog()
    act(() => {
      localStorage.removeItem('football-market-session')
      window.dispatchEvent(new StorageEvent('storage', { key: 'football-market-session' }))
    })
    expect(get.mock.calls[0][1].signal.aborted).toBe(true)
    expect(await screen.findByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })

  it('reinicia el contenedor incluso si se vuelve a guardar el mismo token', async () => {
    const pending = deferred()
    const session = sessionFor(1)
    saveSession(session, true)
    const get = catalogGet().mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ data: [] })
    renderCatalog()
    act(() => { clearSession(); saveSession(session, true) })
    expect(get.mock.calls[0][1].signal.aborted).toBe(true)
    expect(await screen.findByText('No se encontraron jugadores.')).toBeInTheDocument()
    await act(async () => pending.resolve({ data: [player(1, 'Viejo')] }))
    expect(screen.queryByText('Viejo Prueba')).not.toBeInTheDocument()
  })

  it('vence la sesión abierta y cancela sus consultas en curso', async () => {
    vi.useFakeTimers()
    saveSession({ ...sessionFor(1), expiresAt: Date.now() + 1000 }, true)
    const pending = deferred()
    const get = catalogGet().mockReturnValue(pending.promise)
    renderCatalog()
    await act(async () => vi.advanceTimersByTime(1001))
    expect(get.mock.calls[0][1].signal.aborted).toBe(true)
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(getSession()).toBeNull()
  })
})

describe('401 y 403 en las consultas vigentes', () => {
  it.each([401, 403])('catálogo maneja HTTP %s sin cuerpo ProblemDetail', async status => {
    saveSession(sessionFor(1), true)
    catalogGet().mockRejectedValue({ response: { status } })
    renderCatalog()
    if (status === 401) {
      expect(await screen.findByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
      expect(getSession()).toBeNull()
    } else {
      expect(await screen.findByRole('alertdialog')).toHaveTextContent('No tenés permiso')
      expect(getSession().userId).toBe(1)
      expect(screen.getByRole('button', { name: 'Salir' })).toBeInTheDocument()
    }
  })
  it.each([401, 403])('perfil maneja HTTP %s coherentemente con el catálogo', async status => {
    saveSession(sessionFor(1), true)
    catalogGet().mockResolvedValueOnce({ data: [player(1, 'Jugador')] })
      .mockRejectedValueOnce({ response: { status } })
    renderCatalog()
    await userEvent.click(await screen.findByRole('button', { name: 'Ver perfil de Jugador Prueba' }))
    if (status === 401) {
      expect(await screen.findByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
      expect(getSession()).toBeNull()
    } else {
      expect(await screen.findByText('No tenés permiso para realizar esta acción.')).toBeInTheDocument()
      expect(getSession().userId).toBe(1)
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    }
  })
})
