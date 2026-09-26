const metrics = [
  { key: 'goals', label: 'Goles' },
  { key: 'assists', label: 'Asistencias' },
  { key: 'tarjetasAmarillas', label: 'Tarjetas amarillas', marker: 'yellow' },
  { key: 'tarjetasRojas', label: 'Tarjetas rojas', marker: 'red' },
  { key: 'minutosJugados', label: 'Minutos jugados' },
]

export const PlayerProfileDialog = ({ player, stats, onClose }) => {
  if (!player) return null

  const valueOrPending = value => value ?? '—'
  const closeOnBackdrop = event => {
    if (event.target === event.currentTarget) onClose()
  }

  return <>
    <div className="modal-backdrop fade show" />
    <div
      className="modal fade show d-block player-profile-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="player-profile-title"
      onMouseDown={closeOnBackdrop}
      onKeyDown={event => { if (event.key === 'Escape') onClose() }}
      tabIndex={-1}
    >
      <div className="modal-dialog modal-dialog-centered modal-lg">
        <article className="modal-content player-profile-card">
          <header className="player-profile-header">
            <div>
              <p className="player-profile-eyebrow mb-1">Perfil del jugador</p>
              <h2 id="player-profile-title" className="player-profile-name mb-1">
                {player.firstName} {player.lastName}
              </h2>
              <p className="player-profile-team mb-0">{player.currentTeam || '—'}</p>
            </div>
            <button type="button" className="btn btn-outline-light" onClick={onClose}>Cerrar</button>
          </header>
          <div className="player-profile-metrics">
            <section className="player-profile-metric">
              <span>Liga</span>
              <strong>{player.league || '—'}</strong>
            </section>
            {metrics.map(metric => <section className="player-profile-metric" key={metric.key}>
              <span className="d-flex align-items-center gap-2">
                {metric.marker && <i className={`player-card-marker player-card-marker--${metric.marker}`} aria-hidden="true" />}
                {metric.label}
              </span>
              <strong>{valueOrPending(stats?.[metric.key])}</strong>
            </section>)}
          </div>
          {!stats && <p className="player-profile-pending mb-0">Estadísticas pendientes de conexión con el back.</p>}
        </article>
      </div>
    </div>
  </>
}
