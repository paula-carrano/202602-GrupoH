import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { FaArrowLeft, FaArrowRight, FaCircleExclamation, FaFileLines, FaFutbol, FaHouse, FaKey, FaMagnifyingGlass, FaRightFromBracket, FaUser, FaUserGroup, FaXmark } from 'react-icons/fa6'
import { api, getApiError } from '../services/api'

const PAGE_SIZE = 7

function ErrorDialog({ message, onClose }) {
  return <div className="error-overlay" role="presentation">
    <div className="error-dialog" role="alertdialog" aria-modal="true" aria-labelledby="error-title" aria-describedby="error-description">
      <div className="error-dialog__top"><FaCircleExclamation aria-hidden="true" /><h2 id="error-title">UPS, HUBO UN PROBLEMA</h2><button aria-label="Cerrar aviso" onClick={onClose}><FaXmark /></button></div>
      <div className="alert alert-danger" id="error-description">{message}</div>
      <div className="text-end"><button className="btn btn-primary" onClick={onClose}>Cerrar</button></div>
    </div>
  </div>
}

export function Home({ session, onLogout }) {
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
  }, [session.token, retry])

  const filtered = useMemo(() => players.filter(player => `${player.firstName} ${player.lastName}`.toLocaleLowerCase().includes(query.toLocaleLowerCase().trim())), [players, query])
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const visible = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  return <main className="dashboard-page">
    <div className="dashboard shadow-sm">
      <header className="dashboard-header">
        <Link className="dashboard-brand" to="/home"><FaFutbol aria-hidden="true" /><strong>FOOTBALL MARKET PLATFORM</strong><span>[v1.0 · Foundation]</span></Link>
        <div className="dashboard-account"><FaUser aria-hidden="true" /><span>{session.username}</span><button type="button" onClick={onLogout}><FaRightFromBracket aria-hidden="true" /> Salir</button></div>
      </header>
      <div className="dashboard-body">
        <nav className="dashboard-sidebar" aria-label="Navegación principal">
          <Link to="/home"><FaHouse aria-hidden="true" /> Inicio</Link>
          <Link to="/home" className="active" aria-current="page"><FaFutbol aria-hidden="true" /> Jugadores</Link>
          <span className="sidebar-inactive" title="Próximamente"><FaKey aria-hidden="true" /> API Keys</span>
          <a href="http://localhost:8080/swagger-ui/index.html" target="_blank" rel="noreferrer"><FaFileLines aria-hidden="true" /> Swagger Docs</a>
        </nav>
        <section className="catalog-panel">
          <div className="catalog-top"><h1><span className="catalog-icon"><FaUserGroup aria-hidden="true" /></span> CATÁLOGO DE JUGADORES</h1><label className="search-box"><FaMagnifyingGlass aria-hidden="true" /><input aria-label="Buscar jugador por nombre" placeholder="Buscar por nombre…" value={query} onChange={e => { setQuery(e.target.value); setPage(1) }} /></label></div>
          {loading ? <div className="catalog-state" role="status">Cargando jugadores…</div> : error ? <div className="catalog-state"><p>{error}</p><button className="btn btn-primary" onClick={() => setRetry(value => value + 1)}>Reintentar</button></div> : <>
            <div className="table-responsive"><table className="table players-table"><thead><tr><th>ID</th><th>Nombre completo</th><th>Nacimiento</th><th>Nacionalidad</th><th>Posición</th><th>Club actual</th><th>Liga</th></tr></thead><tbody>{visible.map(player => <tr key={player.id}><td>{player.id}</td><td>{player.firstName} {player.lastName}</td><td>{player.birthDate || 'N/D'}</td><td>{player.nationality}</td><td>{player.position}</td><td>{player.currentTeam}</td><td>{player.league}</td></tr>)}</tbody></table>{filtered.length === 0 && <p className="empty-results">No se encontraron jugadores.</p>}</div>
            <div className="catalog-footer"><span>{filtered.length ? `Mostrando ${(page - 1) * PAGE_SIZE + 1}–${Math.min(page * PAGE_SIZE, filtered.length)} de ${filtered.length} jugadores` : 'Mostrando 0 jugadores'}</span><div className="pagination-controls"><button className="btn btn-outline-primary" disabled={page === 1} onClick={() => setPage(page - 1)}><FaArrowLeft aria-hidden="true" /> Anterior</button><button className="btn btn-primary" disabled={page >= totalPages} onClick={() => setPage(page + 1)}>Siguiente <FaArrowRight aria-hidden="true" /></button></div></div>
          </>}
        </section>
      </div>
    </div>
    {modalError && <ErrorDialog message={modalError} onClose={() => setModalError('')} />}
  </main>
}
