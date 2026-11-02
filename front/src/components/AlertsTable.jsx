export function AlertsTable({ alerts, deletingDisabled, onSelectPlayer, onDelete, onOrder }) {
  return (
      <div className="table-responsive"><table className="table"><caption>Alertas configuradas</caption><thead><tr>{['Jugador', 'Condición', 'Objetivo (CRD)', 'Estado', 'Acciones'].map(h => <th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{alerts.map(a => <tr key={a.id}>
        <td><button type="button" className="btn btn-link" onClick={() => onSelectPlayer(a.playerId)}>{a.playerName}</button></td><td>{a.condition === 'ABOVE' ? 'Mayor o igual' : 'Menor o igual'}</td><td>{a.targetPrice}</td><td>{a.status}{a.triggeredAt && <> · {new Date(a.triggeredAt).toLocaleString('es-AR')}</>}</td>
        <td><button type="button" className="btn btn-sm btn-outline-danger" disabled={deletingDisabled} onClick={() => onDelete(a.id)}>Eliminar</button> <button type="button" className="btn btn-sm btn-primary" onClick={() => onOrder('buy', a.playerId)}>Comprar</button> <button type="button" className="btn btn-sm btn-outline-primary" onClick={() => onOrder('sell', a.playerId)}>Vender</button></td>
      </tr>)}</tbody></table></div>
  )
}
