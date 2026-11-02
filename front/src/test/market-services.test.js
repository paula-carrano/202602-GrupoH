import { afterEach, expect, it, vi } from 'vitest'
import { api } from '../services/api'
import { allPages, getQuoteAtDate, placeOrder, topup, createAlert, deleteAlert, getPortfolio, getTransactions, getRanking } from '../services/market'
afterEach(() => vi.restoreAllMocks())
it('consume todas las páginas sin perder jugadores vendidos ni cotizaciones', async () => {
  const get = vi.spyOn(api, 'get').mockResolvedValueOnce({ data: { content: [{ id: 2 }], pageNumber: 0, totalPages: 2, totalElements: 2, last: false } }).mockResolvedValueOnce({ data: { content: [{ id: 1 }], pageNumber: 1, totalPages: 2, totalElements: 2, last: true } })
  const signal = new AbortController().signal
  expect(await allPages('/users/1/transactions', { signal })).toEqual([{ id: 2 }, { id: 1 }])
  expect(get.mock.calls[1][1]).toEqual({ params: { page: 1, size: 100 }, signal })
})
it('rechaza páginas duplicadas o incompletas', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({ data: { content: [{ id: 1 }, { id: 1 }], pageNumber: 0, totalPages: 1, totalElements: 2, last: true } })
  await expect(allPages('/test')).rejects.toThrow('inconsistente')
})
it('rechaza una colección modificada mientras se recorre', async () => {
  vi.spyOn(api, 'get').mockResolvedValueOnce({ data: { content: [{ id: 2 }], pageNumber: 0, totalPages: 2, totalElements: 2, last: false } }).mockResolvedValueOnce({ data: { content: [{ id: 1 }], pageNumber: 1, totalPages: 3, totalElements: 3, last: false } })
  await expect(allPages('/test')).rejects.toThrow('cambió')
})
it('respeta rutas, cuerpos y fecha del backend sin enviar identidad en órdenes', async () => {
  const get = vi.spyOn(api, 'get').mockResolvedValue({ data: {} }), post = vi.spyOn(api, 'post').mockResolvedValue({ data: {} }), del = vi.spyOn(api, 'delete').mockResolvedValue({})
  await getQuoteAtDate(7, '2026-10-01'); await getPortfolio(1); await getTransactions(1, 2); await getRanking()
  await placeOrder('buy', 7, 2); await placeOrder('sell', 7, 1); await topup(1, '10.1234'); await createAlert({ playerId: 7, condition: 'ABOVE', targetPrice: '2.5' }); await deleteAlert(9)
  expect(get.mock.calls.map(c => c[0])).toEqual(['/players/7/quotes', '/users/1/portfolio', '/users/1/transactions', '/players/ranking'])
  expect(get.mock.calls[0][1].params).toEqual({ date: '2026-10-01' })
  expect(post.mock.calls.map(c => c.slice(0, 2))).toEqual([['/orders/buy', { playerId: 7, quantity: 2 }], ['/orders/sell', { playerId: 7, quantity: 1 }], ['/users/1/credits/topup', { amount: '10.1234' }], ['/alerts', { playerId: 7, condition: 'ABOVE', targetPrice: '2.5' }]])
  expect(del.mock.calls[0][0]).toBe('/alerts/9')
})
