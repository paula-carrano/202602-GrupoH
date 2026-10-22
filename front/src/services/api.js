import axios from 'axios'
import { getSession } from './session'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 12000,
})

api.interceptors.request.use(config => {
  const token = getSession()?.token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export const isApiCancellation = error => axios.isCancel(error) || error?.code === 'ERR_CANCELED' || error?.name === 'AbortError'

// ErrorCode.java y GlobalExceptionHandler.java: no mostrar detail arbitrario.
const messages = {
  INVALID_CREDENTIALS: 'Credenciales inválidas',
  USER_NOT_FOUND: 'No se encontró el usuario.',
  EMAIL_ALREADY_EXISTS: 'El email ya está registrado.',
  USERNAME_ALREADY_EXISTS: 'El nombre de usuario ya está registrado.',
  USER_ALREADY_EXISTS: 'El usuario ya está registrado.',
  PLAYER_NOT_FOUND: 'No se encontró el jugador.',
  PLAYER_STATISTICS_NOT_FOUND: 'Estadísticas todavía no disponibles.',
  CATALOG_SYNC_NOT_FOUND: 'No se encontró la sincronización del catálogo.',
  CATALOG_SYNC_BUSY: 'Ya hay una sincronización del catálogo en curso.',
  WHOSCORED_ID_CONFLICT: 'El identificador de WhoScored ya está asociado a otro jugador.',
  API_KEY_NOT_FOUND: 'No se encontró la clave de API.',
  API_KEY_INACTIVE: 'La clave de API no está activa.',
  UNAUTHORIZED_ACCESS: 'La sesión no es válida. Iniciá sesión nuevamente.',
  FORBIDDEN_OPERATION: 'No tenés permiso para realizar esta acción.',
  VALIDATION_FAILED: 'Revisá los datos ingresados.',
  INVALID_PARAMETER_FORMAT: 'El formato de un parámetro no es válido.',
  MALFORMED_JSON: 'No se pudo procesar la solicitud.',
  INTERNAL_SERVER_ERROR: 'Ocurrió un error en el servidor. Intentá nuevamente más tarde.',
}

export const getApiError = error => {
  if (isApiCancellation(error)) return ''
  const code = error.response?.data?.errorCode
  if (Object.hasOwn(messages, code)) return messages[code]
  if (error.response?.status === 401) return messages.UNAUTHORIZED_ACCESS
  if (error.response?.status === 403) return messages.FORBIDDEN_OPERATION
  if (error.code === 'ECONNABORTED') return 'La solicitud tardó demasiado. Intentá nuevamente.'
  if (!error.response) return 'No se pudo conectar con el servidor. Verificá que el backend esté disponible.'
  return 'Ocurrió un error inesperado. Por favor, intentá nuevamente más tarde.'
}
