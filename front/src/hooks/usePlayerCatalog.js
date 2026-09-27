import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getApiError } from '../services/api'
import { getPlayers } from '../services/players'

const PAGE_SIZE = 10

export const usePlayerCatalog = ({ session, onLogout }) => {
  const [players, setPlayers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modalError, setModalError] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [retry, setRetry] = useState(0)
  const navigate = useNavigate()

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    getPlayers({ signal: controller.signal })
      .then(({ data }) => {
        setPlayers(data)
        setLoading(false)
      })
      .catch(requestError => {
        if (requestError.code === 'ERR_CANCELED') return
        if ([401, 403].includes(requestError.response?.status)) {
          onLogout()
          navigate('/login', { replace: true })
          return
        }
        const message = getApiError(requestError)
        setError(message)
        setModalError(message)
        setLoading(false)
      })

    return () => controller.abort()
  }, [session?.token, retry])

  const filtered = useMemo(() => players.filter(player =>
    `${player.firstName} ${player.lastName}`
      .toLocaleLowerCase()
      .includes(query.toLocaleLowerCase().trim()),
  ), [players, query])
  const visible = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  return {
    error,
    filteredCount: filtered.length,
    loading,
    modalError,
    page,
    pageSize: PAGE_SIZE,
    query,
    retry: () => setRetry(value => value + 1),
    setPage,
    setQuery: value => {
      setQuery(value)
      setPage(1)
    },
    closeModalError: () => setModalError(''),
    visible,
  }
}
