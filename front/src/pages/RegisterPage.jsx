import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { FaEnvelope, FaLock, FaUser, FaUserPlus } from 'react-icons/fa6'
import { AuthLayout, FeedbackAlert, FormField } from '../components'
import { useApiRequest } from '../hooks/useApiRequest'
import { register } from '../services/auth'

export const RegisterPage = () => {
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '', confirm: '' })
  const [showPassword, setShowPassword] = useState(false)
  const { loading: busy, error, run, setError } = useApiRequest()
  const change = field => event => setForm(current => ({ ...current, [field]: event.target.value }))

  const submit = event => {
    event.preventDefault()
    if (form.password !== form.confirm) {
      setError('Las contraseñas no coinciden.')
      return
    }
    return run(async () => {
      await register({ username: form.username.trim(), email: form.email.trim(), password: form.password })
      navigate('/login', { state: { registered: true } })
    })
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
