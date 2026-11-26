const formatNumber = (value, decimals = 2) => {
  if (value == null || Number.isNaN(Number(value))) {
    return '0'
  }
  return Number(value).toLocaleString('es-AR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}

const formatInteger = value => {
  if (value == null || Number.isNaN(Number(value))) {
    return '0'
  }
  return Number(value).toLocaleString('es-AR')
}

const formatTimestamp = value => {
  if (!value) {
    return 'Sin registros'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return String(value)
  }
  return parsed.toLocaleString('es-AR')
}

export const AdvancedMetricsPanel = ({ data }) => {
  if (!data) {
    return (
      <output className="d-block alert alert-secondary">
        No hay métricas avanzadas disponibles para mostrar.
      </output>
    )
  }

  const { market = {}, valuation = {}, alerts = {}, performance = {}, generatedAt } = data
  const transactionsByType = market.transactionsByType || {}
  const topPlayers = Array.isArray(market.topTradedPlayers) ? market.topTradedPlayers : []

  return (
    <div className="d-flex flex-column gap-4">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <span className="text-muted small">
          Reporte generado: <strong>{formatTimestamp(generatedAt)}</strong>
        </span>
      </div>

      <div className="row g-3">
        <div className="col-12 col-md-6 col-xl-3">
          <article className="card h-100 shadow-sm">
            <div className="card-body">
              <h2 className="h6 text-muted mb-3">Mercado y Trading</h2>
              <p className="mb-1">Operaciones totales: <strong>{formatInteger(market.totalTransactions)}</strong></p>
              <p className="mb-1">Volumen negociado: <strong>{formatNumber(market.totalTradedCredits)} CRD</strong></p>
              <p className="mb-1">Usuarios activos: <strong>{formatInteger(market.activeUsers)}</strong></p>
              <p className="mb-1">
                Liquidez en circulación:{' '}
                <strong>
                  {formatInteger(market.tokensInCirculation)} / {formatInteger(market.totalIssuedTokens)}
                </strong>{' '}
                ({formatNumber(market.circulationRatioPercent)}%)
              </p>
              <div className="small text-muted mt-2">
                Compra: {formatInteger(transactionsByType.COMPRA)} · Venta: {formatInteger(transactionsByType.VENTA)} ·
                Recarga: {formatInteger(transactionsByType.RECARGA)}
              </div>
            </div>
          </article>
        </div>

        <div className="col-12 col-md-6 col-xl-3">
          <article className="card h-100 shadow-sm">
            <div className="card-body">
              <h2 className="h6 text-muted mb-3">Valoración y Estrategia</h2>
              <p className="mb-1">
                Estrategia activa: <strong>{valuation.activeStrategyName || valuation.activeStrategyCode || 'N/A'}</strong>{' '}
                (v{valuation.activeStrategyVersion ?? 1})
              </p>
              <p className="mb-1">Jugadores cubiertos: <strong>{formatInteger(valuation.coveredPlayers)}</strong></p>
              <p className="mb-1">Cotización promedio: <strong>{formatNumber(valuation.averageQuote)} CRD</strong></p>
              <p className="mb-1">
                Rango: <strong>{formatNumber(valuation.minQuote)}</strong> – <strong>{formatNumber(valuation.maxQuote)} CRD</strong>
              </p>
              <p className="small text-muted mb-0 mt-2">
                Último recálculo: {formatTimestamp(valuation.lastRecalculationAt)}
              </p>
            </div>
          </article>
        </div>

        <div className="col-12 col-md-6 col-xl-3">
          <article className="card h-100 shadow-sm">
            <div className="card-body">
              <h2 className="h6 text-muted mb-3">Motor de Alertas</h2>
              <p className="mb-2">Alertas activas: <strong>{formatInteger(alerts.activeAlerts)}</strong></p>
              <p className="mb-0">Alertas disparadas: <strong>{formatInteger(alerts.triggeredAlerts)}</strong></p>
            </div>
          </article>
        </div>

        <div className="col-12 col-md-6 col-xl-3">
          <article className="card h-100 shadow-sm">
            <div className="card-body">
              <h2 className="h6 text-muted mb-3">Rendimiento y Caché</h2>
              <p className="mb-1">Aciertos (Hits): <strong>{formatInteger(performance.rankingCacheHits)}</strong></p>
              <p className="mb-1">Fallos (Misses): <strong>{formatInteger(performance.rankingCacheMisses)}</strong></p>
              <p className="mb-1">Tasa de aciertos: <strong>{formatNumber(performance.rankingCacheHitRatioPercent)}%</strong></p>
              <p className="mb-0">Latencia promedio ranking: <strong>{formatNumber(performance.avgRankingLatencyMs)} ms</strong></p>
            </div>
          </article>
        </div>
      </div>

      <section className="card shadow-sm">
        <div className="card-body">
          <h2 className="h6 mb-3">Top 5 jugadores más operados</h2>
          {topPlayers.length === 0 ? (
            <p className="text-muted mb-0">Todavía no hay operaciones registradas para calcular el Top 5.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm align-middle mb-0">
                <thead>
                  <tr>
                    <th scope="col">Jugador</th>
                    <th scope="col">Equipo</th>
                    <th scope="col" className="text-end">Tokens operados</th>
                    <th scope="col" className="text-end">Volumen (CRD)</th>
                  </tr>
                </thead>
                <tbody>
                  {topPlayers.map(player => (
                    <tr key={player.playerId}>
                      <td>{player.playerName}</td>
                      <td>{player.team || '-'}</td>
                      <td className="text-end">{formatInteger(player.totalTokensTraded)}</td>
                      <td className="text-end">{formatNumber(player.totalVolumeCredits)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
