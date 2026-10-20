import { Link } from 'react-router-dom'
import { FinancialPanel, FinancialTable, financialValue } from '../components/FinancialPanel'
import { FinancialSummary } from '../components/FinancialSummary'
import { PortfolioAllocationChart } from '../components/charts/PortfolioAllocationChart'
import { WealthHistoryChart } from '../components/charts/WealthHistoryChart'
import { ProfitOrLoss } from '../components/PlayerInvestmentSummary'

// Modelos de presentación inyectados; no son contratos HTTP.
export const PortfolioPage = ({ state = 'unavailable', positions = [], summary, allocation, wealth, onSelectPlayer, onBuy, onSell, ...feedback }) => <>
  <h1 className="h5">Mi portfolio</h1>
  {state === 'unavailable' && <p role="status">Esta sección todavía no está disponible.</p>}
  <div className="mb-3"><FinancialSummary {...summary} /></div>
  <FinancialPanel title="Posiciones" state={state === 'ready' && !positions.length ? 'empty' : state} emptyText="No tenés posiciones en cartera." {...feedback}>
    <FinancialTable caption="Posiciones disponibles" rows={positions} columns={[
      { key: 'playerLabel', label: 'Jugador', render: row => onSelectPlayer ? <button className="btn btn-link" onClick={() => onSelectPlayer(row.playerId)}>{row.playerLabel}</button> : financialValue(row.playerLabel) },
      { key: 'tokenQuantity', label: 'Tokens' }, { key: 'averagePurchasePrice', label: 'Precio promedio (créditos)' },
      { key: 'acquisitionCost', label: 'Costo (créditos)' }, { key: 'marketQuote', label: 'Cotización (créditos)' },
      { key: 'currentValue', label: 'Valor actual (créditos)' }, { key: 'profitOrLoss', label: 'PnL (créditos)', render: row => <ProfitOrLoss value={row.profitOrLoss} /> },
      { key: 'actions', label: 'Operaciones', render: row => <><button className="btn btn-outline-primary" disabled={!onBuy} onClick={() => onBuy(row.playerId)}>Comprar</button> <button className="btn btn-outline-primary" disabled={!onSell || !(row.tokenQuantity > 0)} onClick={() => onSell(row.playerId)}>Vender</button></> },
    ]} />
  </FinancialPanel>
  <Link className="btn btn-link my-2" to="/players">Explorar jugadores</Link>
  <div className="row g-3"><div className="col-12 col-xl-6"><PortfolioAllocationChart {...allocation} /></div><div className="col-12 col-xl-6"><WealthHistoryChart {...wealth} /></div></div>
</>
