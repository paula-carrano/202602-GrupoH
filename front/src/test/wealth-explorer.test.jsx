import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { WealthExplorer } from '../components/WealthExplorer'
import { MarketContext } from '../hooks/useMarket'
import * as marketService from '../services/market'
import * as financialUtils from '../utils/financial'
import { sessionFor } from './fixtures/session'

describe('WealthExplorer', () => {
  const session = sessionFor(1)
  const defaultMarket = { session, revision: 0, onLogout: vi.fn() }

  const renderComponent = (marketValue = defaultMarket) =>
    render(
      <MarketContext.Provider value={marketValue}>
        <WealthExplorer />
      </MarketContext.Provider>
    )

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('obtiene cotizaciones con concurrencia acotada (<= 4) y permite cambiar el período', async () => {
    const portfolio = {
      creditsBalance: 100,
      totalCurrentValue: 50,
      positions: [
        { playerId: 1, tokensQuantity: 1 },
        { playerId: 2, tokensQuantity: 1 },
        { playerId: 3, tokensQuantity: 1 },
        { playerId: 4, tokensQuantity: 1 },
        { playerId: 5, tokensQuantity: 1 },
      ],
    }
    const transactions = [
      { id: 1, timestamp: '2026-10-01T10:00:00Z', operationType: 'COMPRA', playerId: 1, totalAmount: 10 },
      { id: 2, timestamp: '2026-10-02T10:00:00Z', operationType: 'VENTA', playerId: 6, totalAmount: 5 },
      { id: 3, timestamp: '2026-10-03T10:00:00Z', operationType: 'RECARGA', playerId: null, totalAmount: 50 },
    ]

    vi.spyOn(marketService, 'getPortfolio').mockResolvedValue({ data: portfolio })
    vi.spyOn(marketService, 'getAllTransactions').mockResolvedValue(transactions)

    let activeRequests = 0
    let maxConcurrent = 0
    vi.spyOn(marketService, 'getAllQuotes').mockImplementation(async (id) => {
      activeRequests++
      maxConcurrent = Math.max(maxConcurrent, activeRequests)
      await new Promise((resolve) => setTimeout(resolve, 10))
      activeRequests--
      return [{ quoteId: id, price: 10, timestamp: '2026-10-01T00:00:00Z' }]
    })

    const today = new Date().toISOString().slice(0, 10)
    const samplePoints = [
      { date: '2026-01-01', availableBalance: 50, totalWealth: 50 },
      { date: today, availableBalance: 100, totalWealth: 150 },
    ]
    vi.spyOn(financialUtils, 'reconstructWealth').mockReturnValue(samplePoints)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('table', { name: 'Histórico patrimonial disponible' })).toBeInTheDocument()
    })

    // Verifica que la concurrencia máxima respetó el límite de 4 peticiones simultáneas
    expect(maxConcurrent).toBeLessThanOrEqual(4)
    expect(maxConcurrent).toBeGreaterThan(0)
    // 6 jugadores únicos (1, 2, 3, 4, 5 de portfolio y 6 de transacciones)
    expect(marketService.getAllQuotes).toHaveBeenCalledTimes(6)

    // Inicialmente con período '30', se muestra solo el punto de hoy
    expect(screen.getByRole('cell', { name: today })).toBeInTheDocument()
    expect(screen.queryByRole('cell', { name: '2026-01-01' })).not.toBeInTheDocument()

    // Cambiar el selector de período a 'all'
    const select = screen.getByRole('combobox', { name: 'Período patrimonial' })
    fireEvent.change(select, { target: { value: 'all' } })
    expect(select.value).toBe('all')
    expect(screen.getByRole('cell', { name: '2026-01-01' })).toBeInTheDocument()

    // Cambiar el selector de período a '90'
    fireEvent.change(select, { target: { value: '90' } })
    expect(select.value).toBe('90')
  })

  it('muestra el estado de error en WealthHistoryChart cuando reconstructWealth falla', async () => {
    vi.spyOn(marketService, 'getPortfolio').mockResolvedValue({
      data: { creditsBalance: 50, positions: [{ playerId: 1 }] },
    })
    vi.spyOn(marketService, 'getAllTransactions').mockResolvedValue([])
    vi.spyOn(marketService, 'getAllQuotes').mockResolvedValue([])
    vi.spyOn(financialUtils, 'reconstructWealth').mockImplementation(() => {
      throw new Error('cotizaciones faltantes')
    })

    renderComponent()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Historial incompleto: cotizaciones faltantes')
  })

  it('maneja el error de API y permite reintentar la solicitud', async () => {
    let callCount = 0
    vi.spyOn(marketService, 'getPortfolio').mockImplementation(async () => {
      callCount++
      if (callCount === 1) {
        throw new Error('Fallo de red al consultar portfolio')
      }
      return { data: { creditsBalance: 100, positions: [] } }
    })
    vi.spyOn(marketService, 'getAllTransactions').mockResolvedValue([])
    vi.spyOn(financialUtils, 'reconstructWealth').mockReturnValue([])

    renderComponent()

    const alert = await screen.findByRole('alert')
    expect(alert).toBeInTheDocument()

    const retryBtn = screen.getByRole('button', { name: 'Reintentar' })
    fireEvent.click(retryBtn)

    await waitFor(() => {
      expect(callCount).toBe(2)
    })
  })
})
