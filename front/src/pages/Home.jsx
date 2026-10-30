import { useMarket } from '../hooks/useMarket'
import { requestState } from '../utils/financial'
import { QuoteExplorer } from '../components/QuoteExplorer'
import { WealthExplorer } from '../components/WealthExplorer'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useSessionRequest } from '../hooks/useSessionRequest'
import { getPlayers } from '../services/players'
import { FinancialSummary } from '../components/FinancialSummary'
import { QuoteHistoryChart } from '../components/charts/QuoteHistoryChart'
import { PortfolioAllocationChart } from '../components/charts/PortfolioAllocationChart'
import { WealthHistoryChart } from '../components/charts/WealthHistoryChart'
import { PlayerProfileDialog } from '../components/PlayerProfileDialog'

export const DashboardPresentation = ({ summary, quotes, allocation, wealth }) => <div className="row g-3">
  <div className="col-12"><FinancialSummary {...summary} /></div>
  <div className="col-12"><QuoteHistoryChart {...quotes} /></div>
  <div className="col-12 col-xl-6"><PortfolioAllocationChart {...allocation} /></div>
  <div className="col-12 col-xl-6"><WealthHistoryChart {...wealth} /></div>
</div>

export const Home = ({ session, onLogout }) => {
  const market = useMarket()
  const state = market ? requestState(market.portfolio) : "unavailable"
  const [loadPlayers, setLoadPlayers] = useState(false)
  const [selectedId, setSelectedId] = useState('')
  const [showDetail, setShowDetail] = useState(false)
  const catalog = useSessionRequest({ session, requestKey: 'dashboard-players', request: getPlayers, enabled: loadPlayers, onUnauthorized: onLogout })
  const selected = catalog.data?.find(player => String(player.id) === selectedId)
  return <>
    <h1 className="fs-5 fw-bold mb-3">Inicio financiero</h1>
    {!market?.allowed && <p role="status">El resumen financiero todavía no está disponible.</p>}
    <Link className="btn btn-outline-primary mb-3" to="/players">Explorar jugadores</Link>
    <section className="mb-3" aria-label="Selección de jugador">
      {!loadPlayers && <button className="btn btn-primary" onClick={() => setLoadPlayers(true)}>Elegir jugador para cotizaciones</button>}
      {catalog.loading && <p role="status">Cargando jugadores…</p>}
      {catalog.error && <div role="alert">{catalog.error} <button className="btn btn-outline-primary" onClick={catalog.retry}>Reintentar catálogo</button></div>}
      {loadPlayers && !catalog.loading && !catalog.error && catalog.data?.length === 0 && <p role="status">No hay jugadores disponibles.</p>}
      {catalog.data?.length > 0 && <label>Jugador<select className="form-select" value={selectedId} onChange={event => { setSelectedId(event.target.value); setShowDetail(false) }}>
        <option value="">Seleccioná un jugador</option>
        {catalog.data.map(player => <option key={player.id} value={player.id}>{player.firstName} {player.lastName}</option>)}
      </select></label>}
      {selected ? <div className="mt-2"><button className="btn btn-outline-primary" onClick={() => setShowDetail(true)}>Ver detalle del jugador</button></div> : <p className="mt-2">Seleccioná un jugador para consultar sus cotizaciones.</p>}
    </section>
    {market ? <div className="row g-3">
      <div className="col-12"><FinancialSummary state={state} data={market.view.summary} error={market.portfolio.error} onRetry={market.portfolio.retry} /></div>
      <div className="col-12">{selected ? <QuoteExplorer key={selected.id} playerId={selected.id} playerLabel={`${selected.firstName} ${selected.lastName}`} session={session} onUnauthorized={onLogout} revision={market.revision} /> : <QuoteHistoryChart />}</div>
      <div className="col-12 col-xl-6"><PortfolioAllocationChart state={state} positions={market.view.allocation} error={market.portfolio.error} onRetry={market.portfolio.retry} /></div>
      <div className="col-12 col-xl-6"><WealthExplorer /></div>
    </div> : <DashboardPresentation quotes={{ playerLabel: selected ? `${selected.firstName} ${selected.lastName}` : undefined }} />}
    <PlayerProfileDialog player={showDetail ? selected : null} session={session} onUnauthorized={onLogout} onClose={() => setShowDetail(false)} />
  </>
}
