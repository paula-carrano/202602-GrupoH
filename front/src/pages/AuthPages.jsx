import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { FaEye, FaEyeSlash, FaFutbol, FaLock, FaUser, FaUserPlus, FaEnvelope } from 'react-icons/fa6'
import { api, getApiError } from '../services/api'
import { saveSession } from '../services/session'

function BrandPanel() {
  return <aside className="brand-panel" aria-label="Football Market Platform">
    <div className="brand-panel__center">
      <FaFutbol className="brand-panel__ball" aria-hidden="true" />
      <strong>FOOTBALL MARKET<br />PLATFORM</strong>
      <span>Conectando el talento con el mercado</span>
    </div>
    <small>v1.0 · Foundation</small>
  </aside>
}

function AuthLayout({ icon: Icon, title, intro, children, footer }) {
  return <main className="auth-page">
    <div className="auth-shell shadow-sm">
      <BrandPanel />
      <section className="auth-form-panel">
        <div className="auth-heading"><Icon aria-hidden="true" /><h1>{title}</h1></div>
        <p className="auth-intro">{intro}</p>
        {children}
        <p className="auth-switch">{footer}</p>
      </section>
    </div>
  </main>
}

function Field({ icon: Icon, id, label, type = 'text', value, onChange, autoComplete, minLength, maxLength, toggle }) {
  return <div className="input-group field-group">
    <span className="input-group-text"><Icon aria-hidden="true" /></span>
    <input id={id} className="form-control" type={type} name={id} placeholder={label} aria-label={label} value={value} onChange={onChange} autoComplete={autoComplete} minLength={minLength} maxLength={maxLength} required />
    {toggle && <button className="btn field-toggle" type="button" onClick={toggle.action} aria-label={toggle.visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}>{toggle.visible ? <FaEyeSlash /> : <FaEye />}</button>}
  </div>
}

function FormError({ message }) {
  return message ? <div className="alert alert-danger py-2" role="alert">{message}</div> : null
}

export function Login({ onLogin }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      const { data } = await api.post('/auth/login', { username: username.trim(), password })
      const next = { token: data.accessToken, username: username.trim(), expiresAt: Date.now() + data.expiresInSeconds * 1000 }
      saveSession(next, remember)
      onLogin(next)
      navigate('/home', { replace: true })
    } catch (requestError) {
      setError(getApiError(requestError))
    } finally {
      setBusy(false)
    }
  }

  return <AuthLayout icon={FaUser} title="Iniciar sesión" intro="Accedé a tu cuenta para gestionar la plataforma." footer={<>¿No tenés una cuenta? <Link to="/register">Registrate</Link></>}>
    {location.state?.registered && <div className="alert alert-success py-2" role="status">Cuenta creada. Ya podés iniciar sesión.</div>}
    <form onSubmit={submit}>
      <Field id="username" icon={FaUser} label="Usuario" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" />
      <Field id="password" icon={FaLock} label="Contraseña" type={showPassword ? 'text' : 'password'} value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" toggle={{ visible: showPassword, action: () => setShowPassword(!showPassword) }} />
      <label className="remember-option"><input type="checkbox" checked={remember} onChange={e => setRemember(e.target.checked)} /> Recordarme</label>
      <FormError message={error} />
      <button className="btn btn-primary w-100 auth-submit" disabled={busy}>{busy ? 'Ingresando…' : 'Iniciar sesión'}</button>
    </form>
  </AuthLayout>
}

export function Register() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '', confirm: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const change = field => event => setForm(current => ({ ...current, [field]: event.target.value }))

  async function submit(event) {
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
      <Field id="username" icon={FaUser} label="Usuario" value={form.username} onChange={change('username')} autoComplete="username" minLength={3} maxLength={30} />
      <Field id="email" icon={FaEnvelope} label="Email" type="email" value={form.email} onChange={change('email')} autoComplete="email" />
      <Field id="password" icon={FaLock} label="Contraseña" type={showPassword ? 'text' : 'password'} value={form.password} onChange={change('password')} autoComplete="new-password" minLength={8} toggle={{ visible: showPassword, action: () => setShowPassword(!showPassword) }} />
      <Field id="confirm" icon={FaLock} label="Confirmar contraseña" type={showPassword ? 'text' : 'password'} value={form.confirm} onChange={change('confirm')} autoComplete="new-password" minLength={8} />
      <FormError message={error} />
      <button className="btn btn-primary w-100 auth-submit" disabled={busy}>{busy ? 'Creando cuenta…' : 'Registrarse'}</button>
    </form>
  </AuthLayout>
}
