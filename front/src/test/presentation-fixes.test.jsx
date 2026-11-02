import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { TransactionsPage } from '../pages/TransactionsPage'
import { SelectedPlayerCard } from '../components/SelectedPlayerCard'
import { allocationPresentation, PortfolioAllocationChart } from '../components/charts/PortfolioAllocationChart'

it('expone auditoría independiente para operaciones del mismo jugador y recargas', async () => {
  render(<TransactionsPage state="ready" transactions={[
    { id: 1, playerId: 7, type: 'COMPRA', balanceBefore: 10, balanceAfter: 0, tokensBefore: 0, tokensAfter: 2, total: 10, unitPrice: 5, strategyVersion: 1 },
    { id: 2, playerId: 7, type: 'VENTA', balanceBefore: 0, balanceAfter: 5, tokensBefore: 2, tokensAfter: 1, total: 5, unitPrice: 5 },
    { id: 3, type: 'RECARGA', balanceBefore: 5, balanceAfter: 15, total: 10 },
  ]} />)
  for (const id of [1, 2, 3]) await userEvent.click(screen.getByText(`Ver detalle de operación ${id}`))
  const details = [...document.querySelectorAll('details')]
  expect(details.every(d => d.open)).toBe(true)
  expect(within(details[0]).getByText('0.0000')).toBeVisible()
  expect(within(details[1]).getByText('No disponible')).toBeVisible()
  expect(within(details[2]).getAllByText('No aplica')).toHaveLength(2)
})

it('muestra las dos primeras letras del usuario y conserva el avatar del jugador', () => {
  const { rerender } = render(<SelectedPlayerCard username="inversor" />)
  expect(screen.getByText('IN')).toBeInTheDocument()
  rerender(<SelectedPlayerCard username="admin" />)
  expect(screen.getByText('AD')).toBeInTheDocument()
  rerender(<SelectedPlayerCard username="a" />)
  expect(screen.getByText('A')).toBeInTheDocument()
  rerender(<SelectedPlayerCard />)
  expect(document.querySelector('.player-avatar svg')).toBeInTheDocument()
  rerender(<SelectedPlayerCard username="admin" selected={{ firstName: 'Unai', lastName: 'Marrero' }} />)
  expect(screen.getByText('UM')).toBeInTheDocument()
})

it('agrupa 2649 posiciones conservando valores y orden estable sin mutarlas', () => {
  const positions = Array.from({ length: 2649 }, (_, i) => ({ playerId: 2649 - i, playerLabel: `Jugador ${2649 - i}`, currentValue: 40 }))
  const { rows, sectors, total } = allocationPresentation(positions)
  expect(rows[0].playerId).toBe(1)
  expect(positions[0].playerId).toBe(2649)
  expect(sectors).toHaveLength(11)
  expect(sectors.at(-1).playerLabel).toBe('Otros')
  expect(sectors.reduce((s, p) => s + p.currentValue, 0)).toBe(total.toNumber())
  expect(allocationPresentation([]).sectors).toEqual([])
  expect(allocationPresentation(positions.slice(0, 1)).sectors[0].percentage).toBe(100)
})

it('permite recorrer todas las posiciones sin agregar Otros a la tabla', async () => {
  const positions = Array.from({ length: 21 }, (_, i) => ({ playerId: i, playerLabel: `Jugador ${i}`, currentValue: 1 }))
  render(<PortfolioAllocationChart state="ready" positions={positions} />)
  expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(11)
  await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }))
  expect(within(screen.getByRole('table')).getByText('Jugador 10')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }))
  expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(2)
  expect(screen.getByRole('button', { name: /Siguiente/ })).toBeDisabled()
})
