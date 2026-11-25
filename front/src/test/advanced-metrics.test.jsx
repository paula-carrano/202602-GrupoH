import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { AdvancedMetricsPage } from '../pages/AdvancedMetricsPage'
import * as advancedMetricsService from '../services/advancedMetrics'

vi.mock('../services/advancedMetrics', () => ({
  getAdvancedMetrics: vi.fn(),
}))

describe('AdvancedMetricsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders consolidated market, valuation, alerts, performance and generatedAt', async () => {
    advancedMetricsService.getAdvancedMetrics.mockResolvedValue({
      data: {
        market: {
          totalTransactions: 42,
          transactionsByType: { COMPRA: 20, VENTA: 10, RECARGA: 11, CAMBIO_ESTRATEGIA: 1 },
          totalTradedCredits: 1245.5,
          activeUsers: 8,
          totalIssuedTokens: 5000,
          tokensInCirculation: 310,
          circulationRatioPercent: 6.2,
          topTradedPlayers: [
            {
              playerId: 12,
              playerName: 'Kylian Mbappé',
              team: 'Real Madrid',
              totalTokensTraded: 85,
              totalVolumeCredits: 412.25,
            },
          ],
        },
        valuation: {
          activeStrategyCode: 'OFFENSIVE',
          activeStrategyName: 'Rendimiento Ofensivo',
          activeStrategyVersion: 2,
          lastRecalculationAt: '2026-11-24T18:00:00Z',
          coveredPlayers: 50,
          averageQuote: 4.25,
          minQuote: 1,
          maxQuote: 14.8,
        },
        alerts: {
          activeAlerts: 5,
          triggeredAlerts: 3,
        },
        performance: {
          rankingCacheHits: 120,
          rankingCacheMisses: 4,
          rankingCacheHitRatioPercent: 96.77,
          avgRankingLatencyMs: 4.12,
        },
        generatedAt: '2026-11-24T21:35:00.123Z',
      },
    })

    render(<AdvancedMetricsPage />)

    expect(await screen.findByText('Rendimiento Ofensivo')).toBeInTheDocument()
    expect(screen.getByText('Kylian Mbappé')).toBeInTheDocument()
    expect(screen.getByText('Real Madrid')).toBeInTheDocument()
    expect(screen.getByText(/Reporte generado:/i)).toBeInTheDocument()
    expect(screen.getByText('Mercado y Trading')).toBeInTheDocument()
    expect(screen.getByText('Rendimiento y Caché')).toBeInTheDocument()
  })

  it('handles empty metrics safely and allows manual refresh', async () => {
    advancedMetricsService.getAdvancedMetrics.mockResolvedValue({
      data: {
        market: {
          totalTransactions: 0,
          transactionsByType: { COMPRA: 0, VENTA: 0, RECARGA: 0, CAMBIO_ESTRATEGIA: 0 },
          totalTradedCredits: 0,
          activeUsers: 0,
          totalIssuedTokens: 0,
          tokensInCirculation: 0,
          circulationRatioPercent: 0,
          topTradedPlayers: [],
        },
        valuation: {
          activeStrategyCode: 'BALANCED',
          activeStrategyName: 'Rendimiento Global Equilibrado',
          activeStrategyVersion: 1,
          lastRecalculationAt: null,
          coveredPlayers: 0,
          averageQuote: 0,
          minQuote: 0,
          maxQuote: 0,
        },
        alerts: { activeAlerts: 0, triggeredAlerts: 0 },
        performance: {
          rankingCacheHits: 0,
          rankingCacheMisses: 0,
          rankingCacheHitRatioPercent: 0,
          avgRankingLatencyMs: 0,
        },
        generatedAt: '2026-11-25T00:00:00Z',
      },
    })

    const user = userEvent.setup()
    render(<AdvancedMetricsPage />)

    expect(
      await screen.findByText(/Todavía no hay operaciones registradas para calcular el Top 5/i)
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Actualizar métricas/i }))
    await waitFor(() => {
      expect(advancedMetricsService.getAdvancedMetrics).toHaveBeenCalledTimes(2)
    })
  })

  it('shows Spanish access denied error on 403 Forbidden', async () => {
    advancedMetricsService.getAdvancedMetrics.mockRejectedValue({
      response: { status: 403, data: { errorCode: 'ACCESS_DENIED' } },
    })

    render(<AdvancedMetricsPage />)

    expect(
      await screen.findByRole('alert')
    ).toHaveTextContent(/permisos exclusivos de superusuario/i)
  })
})
