import { useCallback, useEffect, useState } from 'react'
import { AdvancedMetricsPanel } from '../components/AdvancedMetricsPanel'
import { getAdvancedMetrics } from '../services/advancedMetrics'
import { getApiError, isApiCancellation } from '../services/api'

export const AdvancedMetricsPage = () => {
  const [state, setState] = useState('loading')
  const [data, setData] = useState(null)
  const [errorMessage, setErrorMessage] = useState('')

  const loadMetrics = useCallback(async signal => {
    setState('loading')
    setErrorMessage('')
    try {
      const response = await getAdvancedMetrics({ signal })
      setData(response.data)
      setState('ready')
    } catch (error) {
      if (isApiCancellation(error)) {
        return
      }
      if (error?.response?.status === 403) {
        setErrorMessage('Acceso denegado: se requieren permisos exclusivos de superusuario para consultar métricas avanzadas.')
      } else {
        setErrorMessage(getApiError(error))
      }
      setState('error')
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    loadMetrics(controller.signal)
    return () => controller.abort()
  }, [loadMetrics])

  return (
    <div className="d-flex flex-column gap-3">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <h1 className="h5 mb-0">Métricas Avanzadas</h1>
        <button
          type="button"
          className="btn btn-outline-primary btn-sm"
          disabled={state === 'loading'}
          onClick={() => loadMetrics()}
        >
          Actualizar métricas
        </button>
      </div>

      {state === 'loading' && (
        <output className="d-block alert alert-info mb-0" aria-live="polite">
          Cargando métricas avanzadas...
        </output>
      )}

      {state === 'error' && (
        <div className="alert alert-danger mb-0" role="alert">
          {errorMessage || 'No se pudieron cargar las métricas avanzadas.'}
        </div>
      )}

      {state === 'ready' && <AdvancedMetricsPanel data={data} />}
    </div>
  )
}
