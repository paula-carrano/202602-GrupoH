import { useLayoutEffect, useRef, useState } from 'react'
import { getApiError, isApiCancellation } from '../services/api'
import { hasSessionIdentity, subscribeSession } from '../services/session'

// Una instancia por recurso. requestKey debe cambiar al cambiar sus parámetros.
// personal exige identidad JWT; nunca usar username como ID ni como autorización.
export const useSessionRequest = ({ session, requestKey, request, enabled = true, personal = false, onUnauthorized }) => {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState(null)
  const generation = useRef(0)
  const callbacks = useRef({ request, onUnauthorized })
  useLayoutEffect(() => { callbacks.current = { request, onUnauthorized } })
  const allowed = enabled && (!personal || hasSessionIdentity(session))
  const key = JSON.stringify([session?.token, session?.userId, requestKey, attempt, allowed])

  useLayoutEffect(() => {
    const id = ++generation.current
    const controller = new AbortController()
    const current = () => generation.current === id && !controller.signal.aborted
    const invalidate = () => {
      controller.abort()
      generation.current++
    }
    const unsubscribe = subscribeSession(() => {
      invalidate()
      setState(null)
    })
    if (allowed) {
      setState({ key, loading: true, data: null, error: '' })
      const execute = async () => {
        try {
          const result = await callbacks.current.request({ signal: controller.signal })
          if (current()) setState({ key, loading: false, data: result.data, error: '' })
        } catch (error) {
          if (!current()) return
          if (isApiCancellation(error)) {
            setState({ key, loading: false, data: null, error: '' })
            return
          }
          setState({ key, loading: false, data: null, error: getApiError(error), status: error.response?.status })
          if (error.response?.status === 401) callbacks.current.onUnauthorized?.()
        }
      }
      void execute()
    }
    return () => { invalidate(); unsubscribe() }
  }, [key, allowed])

  return {
    ...(state?.key === key ? state : { data: null, error: '', loading: allowed }),
    unavailable: enabled && !allowed,
    retry: () => setAttempt(value => value + 1),
  }
}
