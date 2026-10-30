import Decimal from 'decimal.js'

export const decimal = value => {
  if (value == null || value === '') throw new Error('Falta un importe')
  const result = new Decimal(value)
  if (!result.isFinite()) throw new Error('Importe inválido')
  return result
}
export const amount = value => decimal(value).toDecimalPlaces(4).toNumber()
export const requestState = request => request.unavailable ? 'unavailable' : request.loading ? 'loading' : request.status === 403 ? 'forbidden' : request.error ? 'error' : request.data == null ? 'unavailable' : 'ready'
export const positionView = p => ({ playerId: p.playerId, playerLabel: p.playerName, tokenQuantity: p.tokensQuantity, averagePurchasePrice: p.averageBuyPrice, marketQuote: p.currentQuote, currentValue: p.totalCurrentValue, acquisitionCost: p.totalCostBasis, profitOrLoss: p.pnlNominal })
export const portfolioView = data => {
  if (!data) return { positions: [], summary: {}, allocation: [] }
  const positions = (data.positions || []).map(positionView)
  return {
    positions,
    summary: { availableBalance: data.creditsBalance, currentPortfolioValue: data.totalCurrentValue, acquisitionCost: data.totalInvestedCost, profitOrLoss: data.totalPnLNominal, totalWealth: amount(decimal(data.creditsBalance).plus(data.totalCurrentValue)) },
    allocation: positions.map(p => ({ ...p, percentage: decimal(data.totalCurrentValue).isZero() ? 0 : decimal(p.currentValue).div(data.totalCurrentValue).times(100).toDecimalPlaces(2).toNumber() })),
  }
}

// Daily UTC snapshots, anchored to the current portfolio. Values never derive from PnL.
export function reconstructWealth(portfolio, transactions, quotesByPlayer, now = new Date()) {
  const financial = transactions.filter(t => ['COMPRA', 'VENTA', 'RECARGA'].includes(t.operationType))
  if (!financial.length) return []
  const time = value => {
    const result = Date.parse(value)
    if (!Number.isFinite(result)) throw new Error('Fecha inválida')
    return result
  }
  const ordered = [...financial].sort((a, b) => time(b.timestamp) - time(a.timestamp) || b.id - a.id)
  const holdings = new Map((portfolio.positions || []).map(p => [p.playerId, p.tokensQuantity]))
  let balance = decimal(portfolio.creditsBalance)
  const quotes = new Map()
  const players = new Set([...holdings.keys(), ...financial.filter(t => t.playerId != null).map(t => t.playerId)])
  for (const id of players) {
    if (!Array.isArray(quotesByPlayer[id])) throw new Error('Faltan cotizaciones históricas')
    quotes.set(id, [...quotesByPlayer[id]].sort((a, b) => time(a.timestamp) - time(b.timestamp) || a.quoteId - b.quoteId))
  }
  const valuation = cutoff => {
    let value = new Decimal(0)
    for (const [id, quantity] of holdings) {
      if (!quantity) continue
      const history = quotes.get(id)
      let low = 0, high = history.length
      while (low < high) { const mid = (low + high) >>> 1; if (time(history[mid].timestamp) <= cutoff) low = mid + 1; else high = mid }
      value = value.plus(decimal(low ? history[low - 1].price : 1).times(quantity))
    }
    return value
  }
  if (!valuation(now.getTime()).toDecimalPlaces(4).equals(decimal(portfolio.totalCurrentValue))) throw new Error('Las cotizaciones y el portfolio cambiaron. Actualizá los datos.')
  let cursor = 0
  const undo = tx => {
    if (!balance.equals(decimal(tx.balanceAfter))) throw new Error('El historial de saldo no concilia')
    if (tx.operationType !== 'RECARGA') {
      if (!Number.isInteger(tx.tokensBefore) || !Number.isInteger(tx.tokensAfter) || !Number.isInteger(tx.quantity) || tx.quantity <= 0) throw new Error('Tenencias incompletas')
      const delta = tx.operationType === 'COMPRA' ? tx.quantity : -tx.quantity
      if (tx.tokensBefore + delta !== tx.tokensAfter || (holdings.get(tx.playerId) || 0) !== tx.tokensAfter) throw new Error('El historial de tokens no concilia')
      holdings.set(tx.playerId, tx.tokensBefore)
    }
    const expected = tx.operationType === 'COMPRA' ? decimal(tx.balanceBefore).minus(tx.totalAmount) : decimal(tx.balanceBefore).plus(tx.totalAmount)
    if (!expected.equals(balance)) throw new Error('Los importes de la operación no concilian')
    balance = decimal(tx.balanceBefore)
  }
  const firstDay = ordered.at(-1).timestamp.slice(0, 10)
  const lastDay = now.toISOString().slice(0, 10)
  const points = []
  for (let day = Date.parse(`${lastDay}T00:00:00Z`); day >= Date.parse(`${firstDay}T00:00:00Z`); day -= 86400000) {
    const cutoff = Math.min(day + 86400000 - 1, now.getTime())
    while (cursor < ordered.length && time(ordered[cursor].timestamp) > cutoff) undo(ordered[cursor++])
    points.push({ date: new Date(day).toISOString().slice(0, 10), availableBalance: amount(balance), totalWealth: amount(balance.plus(valuation(cutoff))) })
  }
  while (cursor < ordered.length) undo(ordered[cursor++])
  return points.reverse()
}
