import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { App } from '../App'
import { api } from '../services/api'
import { saveSession, clearSession } from '../services/session'
import { sessionFor } from './fixtures/session'
import { connectAlerts } from '../services/alertStream'

vi.mock('../services/alertStream', () => ({ connectAlerts: vi.fn(() => vi.fn()) }))
const player = { id: 7, firstName: 'Jugador', lastName: 'Prueba', active: true }
const position = { playerId: 7, playerName: 'Jugador Prueba', tokensQuantity: 2, averageBuyPrice: 2, currentQuote: 3, totalCurrentValue: 6, totalCostBasis: 4, pnlNominal: 2 }
let portfolio, alerts, get
const paged = content => ({ content, pageNumber: 0, pageSize: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, last: true })
const app = path => render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
beforeEach(() => {
  localStorage.clear(); sessionStorage.clear(); saveSession(sessionFor(1), true)
  portfolio = { userId: 1, creditsBalance: 50, totalCurrentValue: 6, totalInvestedCost: 4, totalPnLNominal: 2, positions: [position] }
  alerts = []
  get = vi.spyOn(api, 'get').mockImplementation(async (url, config) => {
    if (url.includes('/portfolio')) return { data: portfolio }
    if (url.includes('/transactions')) return { data: paged([]) }
    if (url === '/alerts') return { data: alerts }
    if (url === '/players') return { data: [player] }
    if (url === '/players/7') return { data: player }
    if (url === '/players/ranking') return { data: { strategyCode: 'BALANCED', strategyVersion: 2, ranking: [{ rank: 1, playerId: 7, firstName: 'Jugador', lastName: 'Prueba', currentQuote: 3 }] } }
    if (url === '/players/7/quotes') return { data: config?.params?.date ? { price: 3, currency: 'CRD', strategyName: 'Balanced', strategyVersion: 2 } : paged([{ quoteId: 1, price: 3, timestamp: new Date().toISOString(), strategyVersion: 2 }]) }
    throw new Error(`Unexpected URL: ${url}`)
  })
})
afterEach(() => { vi.restoreAllMocks(); vi.clearAllMocks(); localStorage.clear(); sessionStorage.clear() })

it('compra desde ranking, evita doble envío y refresca portfolio', async () => {
  let resolve
  const post = vi.spyOn(api, 'post').mockImplementation(() => new Promise(done => { resolve = done }))
  app('/ranking')
  await userEvent.click(await screen.findByRole('button', { name: 'Comprar', exact: true }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Cantidad de tokens'), '2')
  await userEvent.click(dialog.getByRole('checkbox'))
  const confirm = dialog.getByRole('button', { name: 'Confirmar operación' })
  await waitFor(() => expect(confirm).toBeEnabled())
  fireEvent.click(confirm); fireEvent.click(confirm)
  expect(post).toHaveBeenCalledTimes(1)
  expect(post.mock.calls[0].slice(0, 2)).toEqual(['/orders/buy', { playerId: 7, quantity: 2 }])
  portfolio = { ...portfolio, creditsBalance: 44 }
  await act(async () => resolve({ data: { transactionId: 4, totalAmount: 6, balanceAfter: 44, unitPrice: 3, tokensAfter: 4 } }))
  expect(await screen.findByText('Operación confirmada')).toBeInTheDocument()
  expect(get.mock.calls.filter(c => c[0].includes('/portfolio')).length).toBeGreaterThan(1)
})

it('una respuesta perdida bloquea repetir la orden y pide reconciliar', async () => {
  const post = vi.spyOn(api, 'post').mockRejectedValue({ code: 'ECONNABORTED' })
  app('/ranking')
  await userEvent.click(await screen.findByRole('button', { name: 'Comprar', exact: true }))
  await userEvent.type(screen.getByLabelText('Cantidad de tokens'), '1')
  await userEvent.click(screen.getByRole('checkbox'))
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar operación' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos confirmar')
  expect(screen.getByRole('button', { name: 'Confirmar operación' })).toBeDisabled()
  expect(post).toHaveBeenCalledOnce()
})

it('valida tenencia al vender y ejecuta recarga con el contrato aprobado', async () => {
  const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { transactionId: 9, amountCredited: 10, balanceAfter: 60 } })
  app('/portfolio')
  await userEvent.click(await screen.findByRole('button', { name: 'Vender', exact: true }))
  await userEvent.type(screen.getByLabelText('Cantidad de tokens'), '3')
  await userEvent.click(screen.getByRole('checkbox'))
  expect(screen.getByRole('button', { name: 'Confirmar operación' })).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: 'Cerrar operación' }))
  await userEvent.click(screen.getByRole('button', { name: 'Recargar créditos' }))
  await userEvent.type(screen.getByLabelText('Monto en créditos'), '10.0000')
  await userEvent.click(screen.getByRole('checkbox'))
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar operación' }))
  expect(await screen.findByText('Operación confirmada')).toBeInTheDocument()
  expect(post.mock.calls[0].slice(0, 2)).toEqual(['/users/1/credits/topup', { amount: '10.0000' }])
})

it('crea y elimina alertas; una notificación abre la operación sin ejecutarla', async () => {
  vi.spyOn(api, 'post').mockImplementation(async (url, body) => {
    alerts = [{ id: 8, playerName: 'Jugador Prueba', status: 'ACTIVA', ...body }]
    return { data: alerts[0] }
  })
  const del = vi.spyOn(api, 'delete').mockImplementation(async () => { alerts = []; return { status: 204 } })
  app('/alerts')
  await userEvent.selectOptions(await screen.findByLabelText('Jugador'), '7')
  await userEvent.type(screen.getByLabelText('Precio objetivo (CRD)'), '3')
  await userEvent.click(screen.getByRole('button', { name: 'Crear alerta' }))
  expect(await screen.findByText('Alerta creada.')).toBeInTheDocument()
  await userEvent.click(await screen.findByRole('button', { name: 'Eliminar', exact: true }))
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar eliminación' }))
  expect(await screen.findByText('Alerta eliminada.')).toBeInTheDocument()
  expect(del.mock.calls[0][0]).toBe('/alerts/8')
  const listener = connectAlerts.mock.calls.at(-1)[0]
  const event = { alertId: 8, playerId: 7, playerName: 'Jugador Prueba', currentPrice: 3, targetPrice: 3, triggeredAt: '2026-10-29T12:00:00Z' }
  act(() => { listener.onEvent(event); listener.onEvent(event) })
  const notifications = within(screen.getByRole('region', { name: 'Notificaciones de precios' }))
  expect(notifications.getAllByRole('button', { name: 'Comprar' })).toHaveLength(1)
  await userEvent.click(notifications.getByRole('button', { name: 'Comprar' }))
  expect(screen.getByRole('dialog')).toBeInTheDocument()
  expect(api.post).toHaveBeenCalledTimes(1)
})

it('cierra el stream y descarta la respuesta de una operación al salir', async () => {
  let resolve
  vi.spyOn(api, 'post').mockImplementation(() => new Promise(done => { resolve = done }))
  app('/ranking')
  await userEvent.click(await screen.findByRole('button', { name: 'Comprar', exact: true }))
  await userEvent.type(screen.getByLabelText('Cantidad de tokens'), '1')
  await userEvent.click(screen.getByRole('checkbox'))
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar operación' }))
  const signal = api.post.mock.calls[0][2].signal
  act(() => clearSession())
  expect(signal.aborted).toBe(true)
  await act(async () => resolve({ data: { transactionId: 1 } }))
  expect(screen.queryByText('Operación confirmada')).not.toBeInTheDocument()
  expect(connectAlerts.mock.results[0].value).toHaveBeenCalled()
})

it('consulta cotización a fecha desde el perfil y mantiene estadísticas separadas', async () => {
  app('/players')
  await userEvent.click(await screen.findByRole('button', { name: 'Ver perfil de Jugador Prueba' }))
  const input = screen.getByLabelText('Cotización a fecha')
  fireEvent.change(input, { target: { value: '2026-10-01' } })
  await waitFor(() => expect(get).toHaveBeenCalledWith('/players/7/quotes', expect.objectContaining({ params: { date: '2026-10-01' } })))
  expect(await screen.findByText(/Cotización al 2026-10-01/)).toHaveTextContent('3 CRD')
})

it('pagina el historial en el servidor sin convertir recargas en jugadores', async () => {
  const original = get.getMockImplementation()
  get.mockImplementation(async (url, options) => {
    if (url.includes('/transactions')) return { data: { content: [{ id: options.params.page + 1, timestamp: '2026-10-01T12:00:00Z', operationType: 'RECARGA', playerId: null, totalAmount: 10 }], pageNumber: options.params.page, pageSize: 20, totalElements: 21, totalPages: 2, last: options.params.page === 1 } }
    return original(url, options)
  })
  app('/transactions')
  expect(await screen.findByRole('cell', { name: 'RECARGA' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '—' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /siguiente/i }))
  await waitFor(() => expect(get).toHaveBeenCalledWith('/users/1/transactions', expect.objectContaining({ params: { page: 1, size: 20 } })))
})

it('mantiene una suscripción SSE al navegar dentro de la misma sesión', async () => {
  app('/ranking')
  await screen.findByRole('button', { name: 'Comprar', exact: true })
  expect(connectAlerts).toHaveBeenCalledOnce()
  await userEvent.click(screen.getByRole('link', { name: 'Historial', exact: true }))
  await screen.findByRole('heading', { name: 'Historial', exact: true })
  expect(connectAlerts).toHaveBeenCalledOnce()
})
