import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FaMagnifyingGlass, FaUserGroup } from 'react-icons/fa6'
import { DashboardLayout } from '../components/DashboardLayout'
import { ErrorDialog } from '../components/ErrorDialog'
import { FeedbackAlert } from '../components/FeedbackAlert'
import { Pagination } from '../components/Pagination'
import { PlayerTable } from '../components/PlayerTable'
import { previewPlayers } from '../data/previewPlayers'
import { api, getApiError } from '../services/api'

const PAGE_SIZE = 10

export const Home = ({ session, onLogout, preview = false }) => {
  const [players, setPlayers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modalError, setModalError] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [retry, setRetry] = useState(0)
  const navigate = useNavigate()

  useEffect(() => {
    if (preview) {
      setPlayers(previewPlayers)
      setLoading(false)
      setError('')
      return
    }

    const controller = new AbortController()
    setLoading(true)
    setError('')
    api.get('/players', { headers: { Authorization: `Bearer ${session.token}` }, signal: controller.signal })
      .then(({ data }) => { setPlayers(data); setLoading(false) })
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
  }, [session?.token, retry, preview])

  const filtered = useMemo(() => players.filter(player => `${player.firstName} ${player.lastName}`.toLocaleLowerCase().includes(query.toLocaleLowerCase().trim())), [players, query])
  const visible = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  return <>
    <DashboardLayout session={session} preview={preview} onLogout={onLogout}>
      {preview && <FeedbackAlert message="Vista previa con datos de muestra. Iniciá sesión para consultar el catálogo real." variant="info" role="status" />}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
        <h1 className="catalog-title d-flex align-items-center gap-2 mb-0 fs-5 fw-bold"><span className="catalog-icon d-inline-flex align-items-center justify-content-center rounded-circle"><FaUserGroup aria-hidden="true" /></span> CATÁLOGO DE JUGADORES</h1>
        <label className="input-group catalog-search"><span className="input-group-text bg-white"><FaMagnifyingGlass aria-hidden="true" /></span><input className="form-control border-start-0" aria-label="Buscar jugador por nombre" placeholder="Buscar por nombre…" value={query} onChange={event => { setQuery(event.target.value); setPage(1) }} /></label>
      </div>
      {loading ? <div className="catalog-state d-flex align-items-center justify-content-center gap-2" role="status"><span className="spinner-border spinner-border-sm" aria-hidden="true" /> Cargando jugadores…</div> : error ? <div className="catalog-state d-flex flex-column align-items-center justify-content-center gap-2"><FeedbackAlert message={error} /><button className="btn btn-primary" onClick={() => setRetry(value => value + 1)}>Reintentar</button></div> : <>
        <PlayerTable players={visible} hasResults={filtered.length > 0} />
        <Pagination page={page} pageSize={PAGE_SIZE} total={filtered.length} onPageChange={setPage} />
      </>}
    </DashboardLayout>
    {modalError && <ErrorDialog message={modalError} onClose={() => setModalError('')} />}
  </>
}
