import { useState } from 'react'
import { useMarket } from '../hooks/useMarket'
import { useSessionRequest } from '../hooks/useSessionRequest'
import { getRanking, getTransactions } from '../services/market'
import { requestState } from '../utils/financial'
import { PortfolioPage } from './PortfolioPage'
import { TransactionsPage } from './TransactionsPage'
import { RankingPage } from './RankingPage'
import { WealthExplorer } from '../components/WealthExplorer'
import { Pagination } from '../components/Pagination'

export function ConnectedPortfolio() {
  const m = useMarket(), state = requestState(m.portfolio)
  return <><button className="btn btn-primary mb-3 align-self-start" disabled={!m.allowed} onClick={m.openTopup}>Recargar créditos</button>
    <PortfolioPage state={state} positions={m.view.positions} summary={{ state, data: m.view.summary, error: m.portfolio.error, onRetry: m.portfolio.retry }} allocation={{ state, positions: m.view.allocation, error: m.portfolio.error, onRetry: m.portfolio.retry }} wealthContent={<WealthExplorer />} error={m.portfolio.error} onRetry={m.portfolio.retry} onSelectPlayer={m.selectPlayer} onBuy={id => m.openOrder('buy', id)} onSell={id => m.openOrder('sell', id)} />
  </>
}

export function ConnectedTransactions() {
  const m = useMarket(), [page, setPage] = useState(0)
  const result = useSessionRequest({ session: m.session, personal: true, requestKey: ['transactions', page, m.revision], request: options => getTransactions(m.session.userId, page, options), onUnauthorized: m.onLogout })
  const rows = (result.data?.content || []).map(t => ({ id: t.id, playerId: t.playerId, playerLabel: t.playerName || (t.playerId ? `Jugador ${t.playerId}` : '—'), dateLabel: new Date(t.timestamp).toLocaleString('es-AR'), type: t.operationType, quantity: t.quantity, unitPrice: t.unitPrice, total: t.totalAmount }))
  return <><TransactionsPage state={requestState(result)} transactions={rows} error={result.error} onRetry={result.retry} onSelectPlayer={m.selectPlayer} />
    {result.data && <Pagination itemLabel="operaciones" page={page + 1} pageSize={result.data.pageSize} total={result.data.totalElements} onPageChange={value => setPage(value - 1)} />}
  </>
}

export function ConnectedRanking() {
  const m = useMarket()
  const result = useSessionRequest({ session: m.session, requestKey: ['ranking', m.revision], request: getRanking, onUnauthorized: m.onLogout })
  const rows = (result.data?.ranking || []).map(p => ({ ...p, playerLabel: `${p.firstName} ${p.lastName}`, marketQuote: p.currentQuote }))
  return <RankingPage state={requestState(result)} players={rows} strategy={result.data ? `${result.data.strategyCode} v${result.data.strategyVersion}` : undefined} error={result.error} onRetry={result.retry} onSelectPlayer={m.selectPlayer} onBuy={m.allowed ? id => m.openOrder('buy', id) : undefined} />
}
