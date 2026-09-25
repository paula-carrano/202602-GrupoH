import { useState } from 'react'
import { getApiError } from '../services/api'

export const useApiRequest = () => {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const run = async request => {
    setLoading(true)
    setError('')
    try {
      return await request()
    } catch (requestError) {
      setError(getApiError(requestError))
      return undefined
    } finally {
      setLoading(false)
    }
  }

  return { loading, error, run, setError }
}
