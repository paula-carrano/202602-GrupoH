import axios from 'axios'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 12000,
})

export function getApiError(error) {
  const detail = error.response?.data?.detail
  if (detail) return detail
  if (error.code === 'ECONNABORTED') return 'La solicitud tardó demasiado. Intentá nuevamente.'
  if (!error.response) return 'No se pudo conectar con el servidor. Verificá que el backend esté disponible.'
  return 'Ocurrió un error inesperado. Por favor, intentá nuevamente más tarde.'
}
