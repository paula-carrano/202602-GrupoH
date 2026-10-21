import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { App } from '../App'
import { DashboardLayout } from '../components'
import { api } from '../services/api'
import { saveSession } from '../services/session'

const routes = ['/home', '/players', '/portfolio', '/transactions', '/ranking', '/alerts']
const sessionKey = 'football-market-session'
const signIn = (remember = true) => saveSession({
  token: 'jwt-solo-prueba', username: 'jugador', expiresAt: Date.now() + 60_000,
}, remember)

const RouterProbe = () => {
  const location = useLocation()
  const navigate = useNavigate()
  return <>
    <output aria-label="Ruta actual">{location.pathname}</output>
    <button onClick={() => navigate(-1)}>Volver en el historial de prueba</button>
  </>
}

const renderApp = (path = '/home') => render(
  <MemoryRouter initialEntries={[path]}><App /><RouterProbe /></MemoryRouter>,
)

beforeEach(() => { localStorage.clear(); sessionStorage.clear() })
afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); sessionStorage.clear() })

describe('navegación integrada de App', () => {
  it('abre el dashboard real después del login sin consultar el catálogo', async () => {
    const user = userEvent.setup()
    vi.spyOn(api, 'post').mockResolvedValue({ data: { accessToken: 'jwt-solo-prueba', expiresInSeconds: 3600 } })
    const get = vi.spyOn(api, 'get')
    renderApp('/login')
    await user.type(screen.getByRole('textbox', { name: 'Usuario' }), 'jugador')
    await user.type(screen.getByLabelText('Contraseña'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByRole('heading', { name: 'Inicio financiero' })).toBeInTheDocument()
    expect(screen.getByLabelText('Ruta actual')).toHaveTextContent('/home')
    expect(screen.getByText('El resumen financiero todavía no está disponible.')).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('navega entre Inicio y Jugadores y actualiza el único enlace activo', async () => {
    const user = userEvent.setup()
    signIn()
    vi.spyOn(api, 'get').mockResolvedValue({ data: [] })
    renderApp()
    const nav = within(screen.getByRole('navigation', { name: 'Navegación principal' }))
    const home = nav.getByRole('link', { name: 'Inicio' })
    const players = nav.getByRole('link', { name: 'Jugadores' })
    expect(home).toHaveAttribute('href', '/home')
    expect(players).toHaveAttribute('href', '/players')
    expect(nav.getByRole('link', { current: 'page' })).toBe(home)
    expect(home).toHaveClass('active')
    await user.click(players)
    expect(await screen.findByText('No se encontraron jugadores.')).toBeInTheDocument()
    expect(screen.getByLabelText('Ruta actual')).toHaveTextContent('/players')
    expect(nav.getByRole('link', { current: 'page' })).toBe(players)
    expect(players).toHaveClass('active')
    expect(screen.getByRole('heading', { name: 'CATÁLOGO DE JUGADORES' })).toHaveFocus()
    expect(home).not.toHaveClass('active')
    expect(screen.queryByRole('heading', { name: 'Inicio financiero' })).not.toBeInTheDocument()
    await user.click(home)
    expect(screen.getByRole('heading', { name: 'Inicio financiero' })).toBeInTheDocument()
    expect(nav.getByRole('link', { current: 'page' })).toBe(home)
    expect(players).not.toHaveClass('active')
    await user.click(screen.getByRole('link', { name: 'Explorar jugadores' }))
    expect(await screen.findByText('No se encontraron jugadores.')).toBeInTheDocument()
  })

  it.each(routes)('protege %s sin sesión', async path => {
    const get = vi.spyOn(api, 'get')
    renderApp(path)
    expect(await screen.findByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.getByLabelText('Ruta actual')).toHaveTextContent('/login')
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('rechaza una sesión guardada vencida al entrar al catálogo', async () => {
    saveSession({ token: 'jwt-vencido', username: 'jugador', expiresAt: Date.now() - 1000 }, true)
    const get = vi.spyOn(api, 'get')
    renderApp('/players')
    expect(await screen.findByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(localStorage.getItem(sessionKey)).toBeNull()
    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toHaveFocus()
    expect(get).not.toHaveBeenCalled()
  })

  it.each([true, false])('logout limpia la sesión y protege el historial (recordarme=%s)', async remember => {
    const user = userEvent.setup()
    signIn(remember)
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: [] })
    renderApp()
    await user.click(screen.getByRole('link', { name: 'Jugadores', exact: true }))
    await screen.findByText('No se encontraron jugadores.')
    await user.click(screen.getByRole('button', { name: 'Salir' }))
    expect(await screen.findByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(localStorage.getItem(sessionKey)).toBeNull()
    expect(sessionStorage.getItem(sessionKey)).toBeNull()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Volver en el historial de prueba' }))
    expect(screen.getByLabelText('Ruta actual')).toHaveTextContent('/login')
    expect(screen.queryByRole('heading', { name: 'Inicio financiero' })).not.toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['Portfolio', '/portfolio', 'Mi portfolio'],
    ['Historial', '/transactions', 'Historial'],
    ['Ranking', '/ranking', 'Ranking'],
    ['Alertas', '/alerts', 'Alertas'],
  ])('permite navegar a %s sin inventar consultas financieras', async (label, path, heading) => {
    signIn()
    const get = vi.spyOn(api, 'get')
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('link', { name: label, exact: true }))
    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.getByLabelText('Ruta actual')).toHaveTextContent(path)
    expect(screen.getByText('Esta sección todavía no está disponible.')).toBeInTheDocument()
    expect(screen.getByRole('link', { current: 'page' })).toHaveTextContent(label)
    expect(get).not.toHaveBeenCalled()
  })

  it('conserva el modo preview del layout sin requerir sesión', () => {
    render(<MemoryRouter initialEntries={['/home']}><DashboardLayout preview>Contenido de prueba</DashboardLayout></MemoryRouter>)
    expect(screen.getByText('Vista previa')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login')
    expect(screen.queryByRole('button', { name: 'Salir' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /FOOTBALL MARKET PLATFORM/ })).toBeInTheDocument()
  })
})
