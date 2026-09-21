import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { Home } from '../pages/Home'
import { api } from '../services/api'

const renderHome = props => render(<MemoryRouter><Home onLogout={vi.fn()} {...props} /></MemoryRouter>)

describe('catálogo de jugadores', () => {
  afterEach(() => vi.restoreAllMocks())

  it('permite revisar la vista previa, paginar y buscar sin llamar a la API', async () => {
    const user = userEvent.setup()
    const get = vi.spyOn(api, 'get')
    renderHome({ preview: true, session: null })

    expect(await screen.findByText('Mostrando 1–10 de 12 jugadores')).toBeInTheDocument()
    expect(screen.queryByText('Julián Álvarez')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByText('Julián Álvarez')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Buscar jugador por nombre' }), 'Messi')
    expect(screen.getByText('Lionel Messi')).toBeInTheDocument()
    expect(screen.getByText('Mostrando 1–1 de 1 jugadores')).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('usa el token para cargar jugadores reales y muestra un aviso si falla la API', async () => {
    const user = userEvent.setup()
    const get = vi.spyOn(api, 'get').mockRejectedValue({ response: { status: 500, data: { detail: 'Servicio no disponible' } } })
    renderHome({ preview: false, session: { token: 'jwt-prueba', username: 'jugador' } })

    expect(await screen.findByRole('alertdialog')).toHaveTextContent('Servicio no disponible')
    expect(get).toHaveBeenCalledWith('/players', expect.objectContaining({ headers: { Authorization: 'Bearer jwt-prueba' } }))
    await user.click(screen.getByRole('button', { name: 'Cerrar aviso' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
  })
})
