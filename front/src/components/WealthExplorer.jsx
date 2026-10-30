import { useMemo, useState } from 'react'
import { useMarket } from '../hooks/useMarket'
import { useSessionRequest } from '../hooks/useSessionRequest'
import { getAllQuotes, getAllTransactions, getPortfolio } from '../services/market'
import { reconstructWealth, requestState } from '../utils/financial'
import { WealthHistoryChart } from './charts/WealthHistoryChart'

export function WealthExplorer() {
  const market = useMarket(), [period, setPeriod] = useState('30')
  const history = useSessionRequest({ session: market.session, personal: true, requestKey: ['wealth', market.revision], onUnauthorized: market.onLogout,
    request: async options => {
      const transactions = await getAllTransactions(market.session.userId, options)
      const { data: portfolio } = await getPortfolio(market.session.userId, options)
      const ids = [...new Set([...(portfolio.positions || []).map(p => p.playerId), ...transactions.filter(t => t.playerId != null && ['COMPRA', 'VENTA'].includes(t.operationType)).map(t => t.playerId)])]
      const quotes = {}
      // Bounded concurrency to avoid flooding the API for larger portfolios.
      let index = 0
      await Promise.all(Array.from({ length: Math.min(4, ids.length) }, async () => {
        while (index < ids.length) { const id = ids[index++]; quotes[id] = await getAllQuotes(id, options) }
      }))
      try { return { data: { points: reconstructWealth(portfolio, transactions, quotes), problem: '' } } }
      catch (error) { return { data: { points: [], problem: error.message } } }
    },
  })
  const points = useMemo(() => (history.data?.points || []).filter(p => period === 'all' || Date.parse(p.date) >= Date.now() - Number(period) * 86400000), [history.data, period])
  return <>
    <label className="mb-2">Período patrimonial<select className="form-select" value={period} onChange={e => setPeriod(e.target.value)}><option value="30">30 días</option><option value="90">90 días</option><option value="all">Todo el historial</option></select></label>
    <p className="small">Reconstrucción diaria al cierre UTC con operaciones y cotizaciones reales. El día actual muestra el último estado consultado.</p>
    <WealthHistoryChart state={history.data?.problem ? 'error' : requestState(history)} points={points} error={history.data?.problem ? `Historial incompleto: ${history.data.problem}` : history.error} onRetry={history.retry} />
  </>
}
