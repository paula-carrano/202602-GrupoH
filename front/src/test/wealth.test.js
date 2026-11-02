import { describe, expect, it } from 'vitest'
import { reconstructWealth, portfolioView } from '../utils/financial'

const tx = (id, day, operationType, before, after, extras = {}) => ({ id, timestamp: `2026-10-${day}T12:00:00Z`, operationType, balanceBefore: before, balanceAfter: after, totalAmount: Math.abs(after - before), ...extras })
const history = [tx(1, '01', 'RECARGA', 0, 100), tx(2, '02', 'COMPRA', 100, 80, { playerId: 7, quantity: 10, tokensBefore: 0, tokensAfter: 10 }), tx(3, '04', 'VENTA', 80, 110, { playerId: 7, quantity: 10, tokensBefore: 10, tokensAfter: 0 })]
const quotes = { 7: [{ quoteId: 1, timestamp: '2026-10-02T10:00:00Z', price: 2 }, { quoteId: 2, timestamp: '2026-10-03T10:00:00Z', price: 3 }] }
const current = { creditsBalance: 110, totalCurrentValue: 0, positions: [] }
const now = new Date('2026-10-04T20:00:00Z')

describe('reconstrucción patrimonial con fuente real', () => {
  it('incluye un jugador vendido completamente y variaciones sin operaciones', () => {
    expect(reconstructWealth(current, history, quotes, now)).toEqual([
      { date: '2026-10-01', availableBalance: 100, totalWealth: 100 },
      { date: '2026-10-02', availableBalance: 80, totalWealth: 100 },
      { date: '2026-10-03', availableBalance: 80, totalWealth: 110 },
      { date: '2026-10-04', availableBalance: 110, totalWealth: 110 },
    ])
  })
  it('usa el precio base anterior a la primera cotización y decimales exactos', () => {
    const rows = [tx(1, '01', 'RECARGA', 0, 1.0001), { ...tx(2, '02', 'COMPRA', 1.0001, 0.0001, { playerId: 7, quantity: 1, tokensBefore: 0, tokensAfter: 1 }), totalAmount: '1.0000' }]
    const portfolio = { creditsBalance: '0.0001', totalCurrentValue: 1, positions: [{ playerId: 7, tokensQuantity: 1 }] }
    expect(reconstructWealth(portfolio, rows, { 7: [] }, now).at(-1).totalWealth).toBe(1.0001)
  })
  it('rechaza operaciones faltantes, saldos inconsistentes y cotizaciones incompletas', () => {
    expect(() => reconstructWealth(current, history.slice(1), {}, now)).toThrow(/cotizaciones/)
    expect(() => reconstructWealth({ ...current, creditsBalance: 111 }, history, quotes, now)).toThrow(/saldo/)
    const bad = history.map(t => t.id === 3 ? { ...t, tokensBefore: 9 } : t)
    expect(() => reconstructWealth(current, bad, quotes, now)).toThrow(/tokens/)
  })
  it('rechaza un portfolio que cambió durante la lectura de precios', () => {
    expect(() => reconstructWealth({ creditsBalance: 80, totalCurrentValue: 40, positions: [{ playerId: 7, tokensQuantity: 10 }] }, history.slice(0, 2), quotes, now)).toThrow(/cambiaron/)
  })
  it('no inventa historia para un usuario sin operaciones', () => expect(reconstructWealth(current, [], {}, now)).toEqual([]))
  it('suma saldo y cartera sin sumar PnL dos veces', () => {
    expect(portfolioView({ creditsBalance: 50, totalCurrentValue: 35, totalInvestedCost: 20, totalPnLNominal: 15, positions: [] }).summary.totalWealth).toBe(85)
  })
})
