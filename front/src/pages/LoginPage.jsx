import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { FaLock, FaUser } from 'react-icons/fa6'
import { AuthLayout, FeedbackAlert, FormField } from '../components'
import { useApiRequest } from '../hooks/useApiRequest'
import { login } from '../services/auth'

export const LoginPage = ({ onLogin }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const { loading: busy, error, run } = useApiRequest()

  const submit = event => {
    event.preventDefault()
    return run(async () => {
      const session = await login(username.trim(), password, remember)
      onLogin(session)
      navigate(location.state?.from?.pathname || '/home', { replace: true })
    })
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
