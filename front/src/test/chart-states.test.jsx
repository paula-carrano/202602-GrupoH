import { QuoteHistoryChart, PortfolioAllocationChart, WealthHistoryChart } from '../components'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { financialPresentation } from './fixtures/financialPresentation'

describe.each([QuoteHistoryChart, PortfolioAllocationChart, WealthHistoryChart])('contenedor de gráfico %s', Chart => {
  it.each(['loading', 'unavailable', 'empty', 'forbidden'])('distingue %s sin dibujar datos', state => {
    render(<Chart state={state} />)
    const expected = { loading: 'Cargando datos', unavailable: 'pendiente de integración', empty: Chart === PortfolioAllocationChart ? 'No tenés posiciones' : 'Todavía no hay datos', forbidden: 'No tenés permiso' }
    expect(screen.getByText(new RegExp(expected[state]))).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
  it('permite reintentar el error sin tratarlo como vacío', async () => {
    const retry = vi.fn()
    render(<Chart state="error" onRetry={retry} />)
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(retry).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
  it('ready sin datos se presenta como vacío confirmado', () => {
    render(<Chart state="ready" />)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})

it('cotizaciones conserva punto único, estrategia, cero y desconocidos sin interpolar', () => {
  const { rerender } = render(<QuoteHistoryChart {...financialPresentation.singlePoint} />)
  expect(screen.getByText(/único punto/)).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: '3.5' })).toBeInTheDocument()
  rerender(<QuoteHistoryChart state="partial" points={[{ date: '2026-10-01', value: 0, strategyVersion: 'v2' }, { date: '2026-10-02', value: null }]} />)
  expect(screen.getByRole('cell', { name: '0' })).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: 'v2' })).toBeInTheDocument()
  expect(screen.getAllByRole('cell', { name: 'No disponible' })).toHaveLength(2)
  expect(screen.getByText(/Información parcial/)).toBeInTheDocument()
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
})

it('distribución muestra valores recibidos sin deducir porcentajes', () => {
  render(<PortfolioAllocationChart state="ready" positions={[{ playerLabel: 'SOLO PRUEBA', currentValue: 35, percentage: null }]} />)
  expect(screen.getByRole('cell', { name: '35' })).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: 'No disponible' })).toBeInTheDocument()
})

it('patrimonio y saldo se mantienen como series distintas', () => {
  render(<WealthHistoryChart state="ready" points={[{ date: '2026-10-01', totalWealth: 85, availableBalance: 50 }]} />)
  expect(screen.getByRole('cell', { name: '85' })).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: '50' })).toBeInTheDocument()
  expect(screen.getByText(/único punto/)).toBeInTheDocument()
})
