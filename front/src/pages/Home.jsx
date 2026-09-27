import { useState } from 'react'
import { FaMagnifyingGlass, FaUserGroup } from 'react-icons/fa6'
import { DashboardLayout, ErrorDialog, FeedbackAlert, Pagination, PlayerProfileDialog, PlayerTable } from '../components'
import { usePlayerCatalog } from '../hooks/usePlayerCatalog'

export const Home = ({ session, onLogout }) => {
  const catalog = usePlayerCatalog({ session, onLogout })
  const [selectedPlayer, setSelectedPlayer] = useState(null)

  return <>
    <DashboardLayout session={session} onLogout={onLogout}>
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
        <h1 className="catalog-title d-flex align-items-center gap-2 mb-0 fs-5 fw-bold">
          <span className="catalog-icon d-inline-flex align-items-center justify-content-center rounded-circle"><FaUserGroup aria-hidden="true" /></span>
          CATÁLOGO DE JUGADORES
        </h1>
        <label className="input-group catalog-search">
          <span className="input-group-text bg-white"><FaMagnifyingGlass aria-hidden="true" /></span>
          <input
            className="form-control border-start-0"
            aria-label="Buscar jugador por nombre"
            placeholder="Buscar por nombre…"
            value={catalog.query}
            onChange={event => catalog.setQuery(event.target.value)}
          />
        </label>
      </div>
      {catalog.loading ? (
        <div className="catalog-state d-flex align-items-center justify-content-center gap-2" role="status">
          <span className="spinner-border spinner-border-sm" aria-hidden="true" /> Cargando jugadores…
        </div>
      ) : catalog.error ? (
        <div className="catalog-state d-flex flex-column align-items-center justify-content-center gap-2">
          <FeedbackAlert message={catalog.error} />
          <button className="btn btn-primary" onClick={catalog.retry}>Reintentar</button>
        </div>
      ) : <>
        <PlayerTable
          players={catalog.visible}
          hasResults={catalog.filteredCount > 0}
          rankingOffset={(catalog.page - 1) * catalog.pageSize}
          onPlayerSelect={setSelectedPlayer}
        />
        <Pagination
          page={catalog.page}
          pageSize={catalog.pageSize}
          total={catalog.filteredCount}
          onPageChange={catalog.setPage}
        />
      </>}
    </DashboardLayout>
    <PlayerProfileDialog player={selectedPlayer} onClose={() => setSelectedPlayer(null)} />
    {catalog.modalError && <ErrorDialog message={catalog.modalError} onClose={catalog.closeModalError} />}
  </>
}
