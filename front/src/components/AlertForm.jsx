import { useState } from 'react'
import { playerName } from '../utils/playerDisplay'
import { decimal } from '../utils/financial'

export function AlertForm({ catalog, allowed, busy, uncertain, onCreate }) {
  const [playerId, setPlayerId] = useState(''), [condition, setCondition] = useState('ABOVE'), [price, setPrice] = useState('')
  const [query, setQuery] = useState('')
  const players = (catalog.data || []).filter(player =>
    String(player.id) === playerId || playerName(player).toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()),
  )
  const valid = Boolean(playerId) && /^\d+(\.\d{1,4})?$/.test(price) && decimal(price).gte('0.0001')
  const submit = async event => {
    event.preventDefault()
    if (!allowed || !valid || busy || uncertain) return
    const created = await onCreate({ playerId: Number(playerId), condition, targetPrice: price })
    if (created) setPrice('')
  }
  return (
    <form className="alert-form card card-body mb-3" data-bs-theme="dark" onSubmit={submit}><h2 className="h5">Crear alerta de precio</h2>
      {catalog.loading && <output className="d-block mb-3">Cargando jugadores…</output>}
      {catalog.error && <p role="alert">{catalog.error} <button type="button" onClick={catalog.retry}>Reintentar jugadores</button></p>}
      <input
        type="search"
        className="form-control mb-2"
        aria-label="Buscar jugador para la alerta"
        placeholder="Buscar jugador..."
        value={query}
        onChange={event => setQuery(event.target.value)}
      />
      <label>Jugador{" "}<select size={6} className="form-select alert-player-list mb-2" value={playerId} onChange={e => setPlayerId(e.target.value)} required><option value="">Seleccioná un jugador</option>{players.map(player => <option key={player.id} value={player.id}>{playerName(player)}</option>)}</select></label>
      <label>Condición{" "}<select className="form-select mb-2" value={condition} onChange={e => setCondition(e.target.value)}><option value="ABOVE">Precio mayor o igual</option><option value="BELOW">Precio menor o igual</option></select></label>
      <label>Precio objetivo (CRD){" "}<input className="form-control mb-2" inputMode="decimal" value={price} onChange={e => setPrice(e.target.value)} required /></label>
      <button type="submit" className="btn btn-primary align-self-start" disabled={!allowed || !valid || busy || uncertain}>Crear alerta</button>
    </form>
  )
}
