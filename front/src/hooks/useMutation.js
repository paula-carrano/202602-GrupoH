import { useEffect, useRef, useState } from 'react'
import { getApiError, isApiCancellation } from '../services/api'
import { subscribeSession } from '../services/session'

export function useMutation(onUnauthorized) {
  const [state, setState] = useState({ busy: false, error: '', uncertain: false, result: null })
  const active = useRef(true), request = useRef(null)
  useEffect(() => {
    active.current = true
    const cancel = () => { active.current = false; request.current?.abort() }
    const unsubscribe = subscribeSession(cancel)
    return () => { cancel(); unsubscribe() }
  }, [])
  const run = async action => {
    if (request.current || !active.current) return null
    const controller = new AbortController(); request.current = controller
    setState({ busy: true, error: '', uncertain: false, result: null })
    try {
      const result = await action({ signal: controller.signal })
      if (!active.current) return null
      setState({ busy: false, error: '', uncertain: false, result: result.data ?? true })
      return result
    } catch (error) {
      if (!active.current || isApiCancellation(error)) return null
      const uncertain = !error.response || error.response.status >= 500
      setState({ busy: false, result: null, uncertain, error: uncertain ? 'No pudimos confirmar el resultado. Revisá los datos antes de repetir la operación.' : getApiError(error) })
      if (error.response?.status === 401) onUnauthorized?.()
      return null
    } finally { request.current = null }
  }
  return { ...state, run }
}
