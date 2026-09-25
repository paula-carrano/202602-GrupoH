import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { Home } from '../pages/Home'
import { api } from '../services/api'
import { saveSession } from '../services/session'

const renderHome = session => render(
  <MemoryRouter><Home session={session} onLogout={vi.fn()} /></MemoryRouter>,
)

const makePlayers = () => Array.from({ length: 12 }, (_, index) => ({
  id: index + 1,
  firstName: index === 11 ? 'Julián' : index === 0 ? 'Lionel' : 'Jugador',
  lastName: index === 11 ? 'Álvarez' : index === 0 ? 'Messi' : `${index + 1}`,
  birthDate: null,
  nationality: 'Argentina',
  position: 'FORWARD',
  currentTeam: 'Equipo',
  league: 'PL',
}))

describe('catálogo de jugadores', () => {
  afterEach(() => vi.restoreAllMocks())

  it('permite paginar y buscar en el catálogo', async () => {
    const user = userEvent.setup()
    vi.spyOn(api, 'get').mockResolvedValue({ data: makePlayers() })
    const session = { token: 'jwt-prueba', username: 'jugador', expiresAt: Date.now() + 60_000 }
    saveSession(session, false)
    renderHome(session)

    expect(await screen.findByText('Mostrando 1–10 de 12 jugadores')).toBeInTheDocument()
    expect(screen.queryByText('Julián Álvarez')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByText('Julián Álvarez')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Buscar jugador por nombre' }), 'Messi')
    expect(screen.getByText('Lionel Messi')).toBeInTheDocument()
    expect(screen.getByText('Mostrando 1–1 de 1 jugadores')).toBeInTheDocument()
  })

  it('muestra un aviso cuando falla la API', async () => {
    const user = userEvent.setup()
    const get = vi.spyOn(api, 'get').mockRejectedValue({ response: { status: 500, data: { detail: 'Servicio no disponible' } } })
    const session = { token: 'jwt-prueba', username: 'jugador', expiresAt: Date.now() + 60_000 }
    saveSession(session, false)
    renderHome(session)

    expect(await screen.findByRole('alertdialog')).toHaveTextContent('Servicio no disponible')
    expect(get).toHaveBeenCalledWith('/players', expect.objectContaining({ signal: expect.any(AbortSignal) }))
    await user.click(screen.getByRole('button', { name: 'Cerrar aviso' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
  })
})
