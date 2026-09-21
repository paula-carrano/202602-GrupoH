import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { FaEnvelope, FaLock, FaUser, FaUserPlus } from 'react-icons/fa6'
import { AuthLayout } from '../components/AuthLayout'
import { FeedbackAlert } from '../components/FeedbackAlert'
import { FormField } from '../components/FormField'
import { api, getApiError } from '../services/api'
import { saveSession } from '../services/session'

export const Login = ({ onLogin }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async event => {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      const { data } = await api.post('/auth/login', { username: username.trim(), password })
      const session = { token: data.accessToken, username: username.trim(), expiresAt: Date.now() + data.expiresInSeconds * 1000 }
      saveSession(session, remember)
      onLogin(session)
      navigate('/home', { replace: true })
    } catch (requestError) {
      setError(getApiError(requestError))
    } finally {
      setBusy(false)
    }
  }

  return <AuthLayout icon={FaUser} title="Iniciar sesión" intro="Accedé a tu cuenta para gestionar la plataforma." footer={<>¿No tenés una cuenta? <Link to="/register">Registrate</Link></>}>
    {location.state?.registered && <FeedbackAlert message="Cuenta creada. Ya podés iniciar sesión." variant="success" role="status" />}
    <form onSubmit={submit}>
      <FormField id="username" icon={FaUser} label="Usuario" value={username} onChange={event => setUsername(event.target.value)} autoComplete="username" />
      <FormField id="password" icon={FaLock} label="Contraseña" type={showPassword ? 'text' : 'password'} value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" toggle={{ visible: showPassword, action: () => setShowPassword(value => !value) }} />
      <div className="form-check mb-4">
        <input className="form-check-input" type="checkbox" id="remember" checked={remember} onChange={event => setRemember(event.target.checked)} />
        <label className="form-check-label" htmlFor="remember">Recordarme</label>
      </div>
      <FeedbackAlert message={error} />
      <button className="btn btn-primary w-100 py-2" type="submit" disabled={busy}>{busy ? 'Ingresando…' : 'Iniciar sesión'}</button>
    </form>
  </AuthLayout>
}

export const Register = () => {
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '', confirm: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const change = field => event => setForm(current => ({ ...current, [field]: event.target.value }))

  const submit = async event => {
    event.preventDefault()
    if (form.password !== form.confirm) {
      setError('Las contraseñas no coinciden.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await api.post('/auth/register', { username: form.username.trim(), email: form.email.trim(), password: form.password })
      navigate('/login', { state: { registered: true } })
    } catch (requestError) {
      setError(getApiError(requestError))
    } finally {
      setBusy(false)
    }
  }

  return <AuthLayout icon={FaUserPlus} title="Crear una cuenta" intro="Completá tus datos para comenzar." footer={<>¿Ya tenés una cuenta? <Link to="/login">Iniciá sesión</Link></>}>
    <form onSubmit={submit}>
      <FormField id="username" icon={FaUser} label="Usuario" value={form.username} onChange={change('username')} autoComplete="username" minLength={3} maxLength={30} />
      <FormField id="email" icon={FaEnvelope} label="Email" type="email" value={form.email} onChange={change('email')} autoComplete="email" />
      <FormField id="password" icon={FaLock} label="Contraseña" type={showPassword ? 'text' : 'password'} value={form.password} onChange={change('password')} autoComplete="new-password" minLength={8} toggle={{ visible: showPassword, action: () => setShowPassword(value => !value) }} />
      <FormField id="confirm" icon={FaLock} label="Confirmar contraseña" type={showPassword ? 'text' : 'password'} value={form.confirm} onChange={change('confirm')} autoComplete="new-password" minLength={8} />
      <FeedbackAlert message={error} />
      <button className="btn btn-primary w-100 py-2" type="submit" disabled={busy}>{busy ? 'Creando cuenta…' : 'Registrarse'}</button>
    </form>
  </AuthLayout>
}
