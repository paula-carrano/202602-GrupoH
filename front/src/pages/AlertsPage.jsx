import { useState } from 'react'
import { useMarket } from '../hooks/useMarket'
import { useMutation } from '../hooks/useMutation'
import { useSessionRequest } from '../hooks/useSessionRequest'
import { getPlayers } from '../services/players'
import { createAlert, deleteAlert } from '../services/market'
import { FinancialPanel } from '../components/FinancialPanel'
import { decimal, requestState } from '../utils/financial'

export function AlertsPage() {
  const m = useMarket(), mutation = useMutation(m.onLogout)
  const [playerId, setPlayerId] = useState(''), [condition, setCondition] = useState('ABOVE'), [price, setPrice] = useState('')
  const [deleting, setDeleting] = useState(null), [message, setMessage] = useState('')
  const catalog = useSessionRequest({ session: m.session, enabled: m.allowed, requestKey: 'alert-players', request: getPlayers, onUnauthorized: m.onLogout })
  const valid = Boolean(playerId) && /^\d+(\.\d{1,4})?$/.test(price) && decimal(price).gte('0.0001')
  const submit = async event => {
    event.preventDefault()
    if (!valid || mutation.busy || mutation.uncertain) return
    const response = await mutation.run(options => createAlert({ playerId: Number(playerId), condition, targetPrice: price }, options))
    if (response) { setPrice(''); setMessage('Alerta creada.'); m.refreshAlerts() }
  }
  const remove = async () => {
    if (!deleting || mutation.busy || mutation.uncertain) return
    const response = await mutation.run(options => deleteAlert(deleting, options))
    if (response) { setDeleting(null); setMessage('Alerta eliminada.'); m.refreshAlerts() }
  }
  const state = requestState(m.alerts)
  return <>
    <h1 className="h5">Alertas</h1><p role="status">{m.streamStatus || 'Notificaciones no disponibles sin una sesión identificada.'}</p>
    {message && <p role="status">{message}</p>}
    <form className="card card-body mb-3" onSubmit={submit}><h2 className="h5">Crear alerta de precio</h2>
      {catalog.loading && <p role="status">Cargando jugadores…</p>}
      {catalog.error && <p role="alert">{catalog.error} <button type="button" onClick={catalog.retry}>Reintentar jugadores</button></p>}
      <label>Jugador<select className="form-select mb-2" value={playerId} onChange={e => setPlayerId(e.target.value)} required><option value="">Seleccioná un jugador</option>{(catalog.data || []).map(p => <option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>)}</select></label>
      <label>Condición<select className="form-select mb-2" value={condition} onChange={e => setCondition(e.target.value)}><option value="ABOVE">Precio mayor o igual</option><option value="BELOW">Precio menor o igual</option></select></label>
      <label>Precio objetivo (CRD)<input className="form-control mb-2" inputMode="decimal" value={price} onChange={e => setPrice(e.target.value)} required /></label>
      <button className="btn btn-primary align-self-start" disabled={!m.allowed || !valid || mutation.busy || mutation.uncertain}>Crear alerta</button>
    </form>
    {mutation.error && <p role="alert">{mutation.error}</p>}
    {mutation.uncertain && <p>Actualizá el listado para comprobar el resultado antes de volver a crear o eliminar una alerta. <button onClick={m.refreshAlerts}>Actualizar alertas</button></p>}
    {deleting && <div className="alert alert-warning">¿Eliminar la alerta seleccionada? <button className="btn btn-danger" disabled={mutation.busy || mutation.uncertain} onClick={remove}>Confirmar eliminación</button> <button className="btn btn-secondary" disabled={mutation.busy} onClick={() => setDeleting(null)}>Cancelar</button></div>}
    <FinancialPanel title="Mis alertas" state={state === 'ready' && !m.alerts.data?.length ? 'empty' : state} error={m.alerts.error} onRetry={m.alerts.retry} emptyText="Todavía no creaste alertas.">
      <div className="table-responsive"><table className="table"><caption>Alertas configuradas</caption><thead><tr>{['Jugador', 'Condición', 'Objetivo (CRD)', 'Estado', 'Acciones'].map(h => <th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{(m.alerts.data || []).map(a => <tr key={a.id}>
        <td><button className="btn btn-link" onClick={() => m.selectPlayer(a.playerId)}>{a.playerName}</button></td><td>{a.condition === 'ABOVE' ? 'Mayor o igual' : 'Menor o igual'}</td><td>{a.targetPrice}</td><td>{a.status}{a.triggeredAt && <> · {new Date(a.triggeredAt).toLocaleString('es-AR')}</>}</td>
        <td><button className="btn btn-sm btn-outline-danger" disabled={mutation.busy || mutation.uncertain} onClick={() => setDeleting(a.id)}>Eliminar</button> <button className="btn btn-sm btn-primary" onClick={() => m.openOrder('buy', a.playerId)}>Comprar</button> <button className="btn btn-sm btn-outline-primary" onClick={() => m.openOrder('sell', a.playerId)}>Vender</button></td>
      </tr>)}</tbody></table></div>
    </FinancialPanel>
  </>
}
