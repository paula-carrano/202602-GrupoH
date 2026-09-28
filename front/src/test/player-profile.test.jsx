import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PlayerProfileDialog } from '../components/PlayerProfileDialog'
import { api } from '../services/api'

const player = { id: 7, firstName: 'Lionel', lastName: 'Messi' }
afterEach(() => vi.restoreAllMocks())

describe('perfil del jugador', () => {
  it('no consulta sin seleccion y muestra carga, detalle y cierre', async () => {
    let resolve
    const get = vi.spyOn(api, 'get').mockReturnValue(new Promise(r => { resolve = r }))
    const onClose = vi.fn()
    const view = render(<PlayerProfileDialog player={null} onClose={onClose} />)
    expect(get).not.toHaveBeenCalled()
    view.rerender(<PlayerProfileDialog player={player} onClose={onClose} />)
    expect(screen.getByText('Cargando perfil…')).toBeInTheDocument()
    await act(async () => resolve({ data: { ...player, currentTeam: 'Argentina', league: 'Internacional', statistics: { goals: 12, assists: 3, yellowCards: 0, redCards: 0, minutesPlayed: 900 } } }))
    expect(screen.getByText('Argentina')).toBeInTheDocument()
    expect(screen.getByText('Internacional')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText('900')).toBeInTheDocument()
    expect(screen.queryByText('Cargando perfil…')).not.toBeInTheDocument()
    fireEvent(screen.getByRole('dialog'), new Event('cancel', { bubbles: true }))
    expect(onClose).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(onClose).toHaveBeenCalledTimes(2)
  })

  it('muestra valores pendientes cuando no hay estadisticas', async () => {
    vi.spyOn(api, 'get').mockResolvedValue({ data: player })
    render(<PlayerProfileDialog player={player} onClose={vi.fn()} />)
    expect(await screen.findByText('Estadísticas todavía no disponibles.')).toBeInTheDocument()
    expect(screen.getAllByText('—')).toHaveLength(7)
  })

  it('muestra el error de la consulta', async () => {
    vi.spyOn(api, 'get').mockRejectedValue({ response: { data: { detail: 'Perfil no disponible' } } })
    render(<PlayerProfileDialog player={player} onClose={vi.fn()} />)
    expect(await screen.findByText('Perfil no disponible')).toBeInTheDocument()
    expect(screen.queryByText('Estadísticas todavía no disponibles.')).not.toBeInTheDocument()
  })

  it('aborta al quitar la seleccion y omite errores de cancelacion', async () => {
    let reject
    const get = vi.spyOn(api, 'get').mockReturnValue(new Promise((_, r) => { reject = r }))
    const view = render(<PlayerProfileDialog player={player} onClose={vi.fn()} />)
    const signal = get.mock.calls[0][1].signal
    view.rerender(<PlayerProfileDialog player={null} onClose={vi.fn()} />)
    expect(signal.aborted).toBe(true)
    await act(async () => reject({ code: 'ERR_CANCELED' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
