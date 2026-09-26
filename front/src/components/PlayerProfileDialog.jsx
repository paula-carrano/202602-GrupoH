import { useEffect, useState } from 'react'
import { getApiError } from '../services/api'
import { getPlayer } from '../services/players'

const metrics = [
  { key: 'goals', label: 'Goles' },
  { key: 'assists', label: 'Asistencias' },
  { key: 'yellowCards', label: 'Tarjetas amarillas', marker: 'yellow' },
  { key: 'redCards', label: 'Tarjetas rojas', marker: 'red' },
  { key: 'minutesPlayed', label: 'Minutos jugados' },
]

export const PlayerProfileDialog = ({ player, onClose }) => {
  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!player) {
      setDetail(null)
      setError('')
      return
    }

    const controller = new AbortController()
    setDetail(null)
    setError('')
    setLoading(true)
    getPlayer(player.id, { signal: controller.signal })
      .then(({ data }) => setDetail(data))
      .catch(requestError => {
        if (requestError.code !== 'ERR_CANCELED') setError(getApiError(requestError))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [player?.id])

  if (!player) return null

  const valueOrPending = value => value ?? '—'
  const profile = detail || player
  const stats = detail?.statistics
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
                {profile.firstName} {profile.lastName}
              </h2>
              <p className="player-profile-team mb-0">{profile.currentTeam || '—'}</p>
            </div>
            <button type="button" className="btn btn-outline-light" onClick={onClose}>Cerrar</button>
          </header>
          <div className="player-profile-metrics">
            <section className="player-profile-metric">
              <span>Liga</span>
              <strong>{profile.league || '—'}</strong>
            </section>
            {metrics.map(metric => <section className="player-profile-metric" key={metric.key}>
              <span className="d-flex align-items-center gap-2">
                {metric.marker && <i className={`player-card-marker player-card-marker--${metric.marker}`} aria-hidden="true" />}
                {metric.label}
              </span>
              <strong>{valueOrPending(stats?.[metric.key])}</strong>
            </section>)}
          </div>
          {loading && <p className="player-profile-status mb-0" role="status"><span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />Cargando perfil…</p>}
          {error && <p className="player-profile-status player-profile-status--error mb-0" role="alert">{error}</p>}
          {!loading && !error && !stats && <p className="player-profile-status mb-0">Estadísticas todavía no disponibles.</p>}
        </article>
      </div>
    </div>
  </>
}
