import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { PortfolioPage } from '../pages/PortfolioPage'
import { TransactionsPage } from '../pages/TransactionsPage'
import { RankingPage } from '../pages/RankingPage'
import { PlayerProfileDialog } from '../components/PlayerProfileDialog'
import { PlayerInvestmentSummary } from '../components/PlayerInvestmentSummary'
import { financialPresentation } from './fixtures/financialPresentation'
import { api } from '../services/api'

afterEach(() => vi.restoreAllMocks())
describe.each([PortfolioPage, TransactionsPage, RankingPage])('vista de presentación %s', Page => {
  it.each(['loading', 'error', 'empty', 'unavailable', 'forbidden'])('representa %s sin consultas', state => {
    const get = vi.spyOn(api, 'get')
    render(<MemoryRouter><Page state={state} /></MemoryRouter>)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
    expect(screen.getAllByRole(state === 'error' || state === 'forbidden' ? 'alert' : 'status').length).toBeGreaterThan(0)
  })
})
it('separa cotización pública de precio promedio personal', async () => {
  const fixture = financialPresentation.marketAndPosition
  vi.spyOn(api, 'get').mockResolvedValue({ data: { id: 7, firstName: 'Jugador', lastName: 'Prueba' } })
  render(<PlayerProfileDialog player={{ id: 7 }} marketQuote={fixture.marketQuote} investment={{ state: 'ready', position: fixture.position }} />)
  await screen.findByText('Jugador Prueba')
  expect(within(screen.getByRole('region', { name: 'Mercado' })).getByText(/Cotización actual.*3.5/)).toBeInTheDocument()
  const personal = within(screen.getByRole('region', { name: 'Mi inversión' }))
  expect(personal.getByText('2')).toBeInTheDocument()
  expect(personal.getByText('Ganancia: +15')).toBeInTheDocument()
})
it('posición vacía no equivale a error y nunca habilita vender', async () => {
  const retry = vi.fn()
  const view = render(<PlayerInvestmentSummary state="empty" />)
  expect(screen.getByText('No tenés tokens de este jugador.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Vender tokens' })).toBeDisabled()
  view.rerender(<PlayerInvestmentSummary state="error" onRetry={retry} />)
  expect(screen.queryByText('No tenés tokens de este jugador.')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  expect(retry).toHaveBeenCalledTimes(1)
})
it('portfolio muestra valores recibidos y permite seleccionar jugador', async () => {
  const select = vi.fn()
  const fixture = financialPresentation.marketAndPosition
  render(<MemoryRouter><PortfolioPage state="ready" onSelectPlayer={select} positions={[{ ...fixture.position, playerId: 7, playerLabel: 'SOLO PRUEBA', marketQuote: fixture.marketQuote }]} summary={{ state: 'ready', data: { availableBalance: 50, currentPortfolioValue: 35, totalWealth: 85, profitOrLoss: 15 } }} /></MemoryRouter>)
  expect(screen.getByText('85')).toBeInTheDocument()
  expect(screen.queryByText('100')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'SOLO PRUEBA' }))
  expect(select).toHaveBeenCalledWith(7)
  expect(screen.getByRole('button', { name: 'Comprar' })).toBeDisabled()
})
it('ranking conserva orden recibido y no inventa posiciones', () => {
  render(<RankingPage state="ready" players={[{ playerLabel: 'Primero', marketQuote: 2 }, { playerLabel: 'Segundo', marketQuote: 5 }]} />)
  const rows = screen.getAllByRole('row')
  expect(rows[1]).toHaveTextContent('Primero')
  expect(rows[2]).toHaveTextContent('Segundo')
  expect(within(rows[1]).getByRole('cell', { name: 'No disponible' })).toBeInTheDocument()
})
it('historial no calcula importes ni inventa campos ausentes', () => {
  render(<TransactionsPage state="partial" transactions={[{ dateLabel: '20/10/2026 12:00', type: 'COMPRA', playerLabel: 'SOLO PRUEBA', quantity: 2, unitPrice: 3.5 }]} />)
  expect(screen.getByRole('cell', { name: 'No disponible' })).toBeInTheDocument()
  expect(screen.queryByRole('cell', { name: '7' })).not.toBeInTheDocument()
})

it('sin identidad personal solo consulta el perfil deportivo existente', async () => {
  const get = vi.spyOn(api, 'get').mockResolvedValue({ data: { id: 7, firstName: 'Jugador', lastName: 'Prueba' } })
  render(<PlayerProfileDialog session={{ token: 'sin-identidad' }} player={{ id: 7 }} />)
  await screen.findByText('Jugador Prueba')
  expect(get.mock.calls.map(call => call[0])).toEqual(['/players/7'])
  expect(within(screen.getByRole('region', { name: 'Mi inversión' })).getByRole('status')).toHaveTextContent('pendiente de integración')
  expect(screen.queryByText('No tenés tokens de este jugador.')).not.toBeInTheDocument()
})
