import { useMemo, useState } from 'react'
import { useSessionRequest } from '../hooks/useSessionRequest'
import { getAllQuotes, getQuoteAtDate } from '../services/market'
import { requestState } from '../utils/financial'
import { QuoteHistoryChart } from './charts/QuoteHistoryChart'

export function QuoteExplorer({ playerId, playerLabel, session, onUnauthorized, revision = 0 }) {
  const [date, setDate] = useState(''), [period, setPeriod] = useState('30')
  const history = useSessionRequest({ session, enabled: Boolean(playerId), requestKey: ['quotes', playerId, revision], request: async options => ({ data: await getAllQuotes(playerId, options) }), onUnauthorized })
  const current = useSessionRequest({ session, enabled: Boolean(playerId), requestKey: ['quote-date', playerId, date, revision], request: options => getQuoteAtDate(playerId, date || new Date().toISOString().slice(0, 10), options), onUnauthorized })
  const points = useMemo(() => (history.data || []).map(q => ({ date: q.timestamp, value: q.price, strategyVersion: q.strategyVersion })).sort((a, b) => a.date.localeCompare(b.date)).filter(q => period === 'all' || Date.parse(q.date) >= Date.now() - Number(period) * 86400000), [history.data, period])
  return <section aria-label="Cotizaciones del jugador">
    <div className="d-flex flex-wrap gap-3 mb-2"><label>Período<select className="form-select" value={period} onChange={e => setPeriod(e.target.value)}><option value="30">30 días</option><option value="90">90 días</option><option value="all">Todo el historial</option></select></label>
    <label>Cotización a fecha<input className="form-control" type="date" value={date} max={new Date().toISOString().slice(0, 10)} onChange={e => setDate(e.target.value)} /></label>{date && <button className="btn btn-link" onClick={() => setDate('')}>Ver cotización actual</button>}</div>
    {current.loading && <p role="status">Cargando cotización…</p>}
    {current.error && <p role="alert">{current.error} <button onClick={current.retry}>Reintentar cotización</button></p>}
    {current.data && <p>{date ? `Cotización al ${date}` : 'Cotización actual'}: <strong>{current.data.price} {current.data.currency}</strong> · {current.data.strategyName} v{current.data.strategyVersion}{current.data.quoteId == null ? ' · Valor base inicial' : ''}</p>}
    <QuoteHistoryChart state={requestState(history)} points={points} playerLabel={playerLabel} error={history.error} onRetry={history.retry} />
  </section>
}
