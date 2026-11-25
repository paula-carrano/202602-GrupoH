import axios from 'axios'
import { getSession } from './session'

const resolveMetricsBaseUrl = () => {
  if (import.meta.env.VITE_METRICS_BASE_URL) {
    return import.meta.env.VITE_METRICS_BASE_URL
  }
  const apiBase = import.meta.env.VITE_API_BASE_URL
  if (apiBase && /^https?:\/\//i.test(apiBase)) {
    return apiBase.replace(/\/api\/?$/i, '')
  }
  if (import.meta.env.DEV) {
    return 'http://localhost:8080'
  }
  return ''
}

export const metricsHttp = axios.create({
  baseURL: resolveMetricsBaseUrl(),
  timeout: 12000,
})

metricsHttp.interceptors.request.use(config => {
  const token = getSession()?.token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export const getAdvancedMetrics = ({ signal } = {}) =>
  metricsHttp.get('/metrics/advanced', { signal })
