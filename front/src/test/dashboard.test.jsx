import { afterEach, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { DashboardPresentation, Home } from '../pages/Home'
import { api } from '../services/api'

afterEach(() => vi.restoreAllMocks())
it('compone las tres áreas sin selección ni consultas financieras', () => {
  const get = vi.spyOn(api, 'get')
  render(<MemoryRouter><Home /></MemoryRouter>)
  for (const name of ['Resumen financiero', 'Evolución histórica de cotizaciones', 'Distribución de la cartera', 'Evolución del patrimonio y saldo']) expect(screen.getByRole('heading', { name })).toBeInTheDocument()
  expect(screen.getByText(/Seleccioná un jugador para consultar/)).toBeInTheDocument()
  expect(get).not.toHaveBeenCalled()
})
it('un error de cotizaciones no oculta el resumen ni confunde cero con desconocido', async () => {
  const retry = vi.fn()
  render(<DashboardPresentation summary={{ state: 'partial', data: { availableBalance: 0, totalWealth: 85, profitOrLoss: 15 } }} quotes={{ state: 'error', onRetry: retry }} allocation={{ state: 'empty' }} wealth={{ state: 'loading' }} />)
  expect(screen.getByText('0')).toBeInTheDocument()
  expect(screen.getByText('85')).toBeInTheDocument()
  expect(screen.queryByText('100')).not.toBeInTheDocument()
  expect(screen.getAllByText('No disponible').length).toBeGreaterThan(0)
  await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  expect(retry).toHaveBeenCalledTimes(1)
})
it('selecciona jugador del catálogo real, abre perfil y restaura foco al cerrar con Escape', async () => {
  const user = userEvent.setup()
  const player = { id: 7, firstName: 'Jugador', lastName: 'Prueba' }
  const get = vi.spyOn(api, 'get').mockResolvedValueOnce({ data: [player] }).mockResolvedValueOnce({ data: player })
  render(<MemoryRouter><Home /></MemoryRouter>)
  await user.click(screen.getByRole('button', { name: 'Elegir jugador para cotizaciones' }))
  await user.selectOptions(await screen.findByRole('combobox', { name: 'Jugador' }), '7')
  const trigger = screen.getByRole('button', { name: 'Ver detalle del jugador' })
  await user.click(trigger)
  const dialog = screen.getByRole('dialog')
  expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toHaveFocus()
  await user.tab()
  expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toHaveFocus()
  await user.tab({ shift: true })
  expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toHaveFocus()
  await user.keyboard('{Escape}')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(trigger).toHaveFocus()
  expect(get.mock.calls.map(call => call[0])).toEqual(['/players', '/players/7'])
})
it('recupera un error de catálogo y distingue vacío confirmado', async () => {
  vi.spyOn(api, 'get').mockRejectedValueOnce({ response: { status: 403 } }).mockResolvedValueOnce({ data: [] })
  render(<MemoryRouter><Home /></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Elegir jugador para cotizaciones' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('No tenés permiso')
  await userEvent.click(screen.getByRole('button', { name: 'Reintentar catálogo' }))
  expect(await screen.findByText('No hay jugadores disponibles.')).toBeInTheDocument()
})
