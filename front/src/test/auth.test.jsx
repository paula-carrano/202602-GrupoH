import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { Login, Register } from '../pages/AuthPages'
import { api } from '../services/api'

const renderRoute = (path, element) => render(
  <MemoryRouter initialEntries={[path]}>
    <Routes>
      <Route path={path} element={element} />
      <Route path="/home" element={<p>Destino home</p>} />
      <Route path="/login" element={path === '/login' ? element : <p>Destino login</p>} />
    </Routes>
  </MemoryRouter>,
)

describe('autenticación', () => {
  beforeEach(() => { localStorage.clear(); sessionStorage.clear() })
  afterEach(() => vi.restoreAllMocks())

  it('envía usuario y contraseña, guarda la sesión y abre Home', async () => {
    const user = userEvent.setup()
    const onLogin = vi.fn()
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { accessToken: 'token-de-prueba', expiresInSeconds: 3600 } })
    renderRoute('/login', <Login onLogin={onLogin} />)

    await user.type(screen.getByRole('textbox', { name: 'Usuario' }), 'messi')
    await user.type(screen.getByLabelText('Contraseña'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByText('Destino home')).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('/auth/login', { username: 'messi', password: 'password123' })
    expect(onLogin).toHaveBeenCalledWith(expect.objectContaining({ token: 'token-de-prueba', username: 'messi' }))
    expect(JSON.parse(localStorage.getItem('football-market-session')).token).toBe('token-de-prueba')
  })

  it('muestra el error de credenciales que devuelve la API', async () => {
    const user = userEvent.setup()
    vi.spyOn(api, 'post').mockRejectedValue({ response: { data: { detail: 'Credenciales inválidas' } } })
    renderRoute('/login', <Login onLogin={vi.fn()} />)

    await user.type(screen.getByRole('textbox', { name: 'Usuario' }), 'messi')
    await user.type(screen.getByLabelText('Contraseña'), 'incorrecta')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales inválidas')
  })

  it('registra con los campos que acepta el backend y dirige al login', async () => {
    const user = userEvent.setup()
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { id: 1 } })
    renderRoute('/register', <Register />)

    await user.type(screen.getByRole('textbox', { name: 'Usuario' }), 'jugador')
    await user.type(screen.getByRole('textbox', { name: 'Email' }), 'jugador@example.com')
    await user.type(screen.getByLabelText('Contraseña', { exact: true }), 'password123')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Registrarse' }))

    expect(await screen.findByText('Destino login')).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('/auth/register', { username: 'jugador', email: 'jugador@example.com', password: 'password123' })
  })

  it('impide enviar contraseñas distintas', async () => {
    const user = userEvent.setup()
    const post = vi.spyOn(api, 'post')
    renderRoute('/register', <Register />)

    await user.type(screen.getByRole('textbox', { name: 'Usuario' }), 'jugador')
    await user.type(screen.getByRole('textbox', { name: 'Email' }), 'jugador@example.com')
    await user.type(screen.getByLabelText('Contraseña', { exact: true }), 'password123')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'different123')
    await user.click(screen.getByRole('button', { name: 'Registrarse' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Las contraseñas no coinciden.')
    expect(post).not.toHaveBeenCalled()
  })
})
