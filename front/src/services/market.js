import { api } from './api'

export const getPortfolio = (id, { signal } = {}) => api.get(`/users/${id}/portfolio`, { signal })
export const getTransactions = (id, page = 0, { signal } = {}) => api.get(`/users/${id}/transactions`, { params: { page, size: 20 }, signal })
export const getRanking = ({ signal } = {}) => api.get('/players/ranking', { signal })
export const getQuoteAtDate = (id, date, { signal } = {}) => api.get(`/players/${id}/quotes`, { params: { date }, signal })
export const placeOrder = (side, playerId, quantity, { signal } = {}) => {
  if (!['buy', 'sell'].includes(side)) throw new Error('Operación inválida')
  return api.post(`/orders/${side}`, { playerId, quantity }, { signal })
}
export const topup = (id, amount, { signal } = {}) => api.post(`/users/${id}/credits/topup`, { amount }, { signal })
export const getAlerts = ({ signal } = {}) => api.get('/alerts', { signal })
export const createAlert = (body, { signal } = {}) => api.post('/alerts', body, { signal })
export const deleteAlert = (id, { signal } = {}) => api.delete(`/alerts/${id}`, { signal })

// Never silently present a truncated or shifting paginated response as complete.
export async function allPages(url, { signal } = {}) {
  const rows = [], ids = new Set()
  let total
  for (let page = 0; ; page++) {
    const { data } = await api.get(url, { params: { page, size: 100 }, signal })
    if (!Array.isArray(data.content) || data.pageNumber !== page || !Number.isInteger(data.totalPages)) throw new Error('Historial incompleto')
    if (total !== undefined && total !== data.totalElements) throw new Error('El historial cambió durante la consulta')
    total = data.totalElements
    for (const row of data.content) {
      const id = row.id ?? row.quoteId
      if (id == null || ids.has(id)) throw new Error('Historial inconsistente')
      ids.add(id); rows.push(row)
    }
    if (data.last || page + 1 >= data.totalPages) break
    if (!data.content.length) throw new Error('Historial incompleto')
  }
  if (rows.length !== total) throw new Error('Historial incompleto')
  return rows
}
export const getAllQuotes = (id, options) => allPages(`/players/${id}/quotes`, options)
export const getAllTransactions = (id, options) => allPages(`/users/${id}/transactions`, options)
