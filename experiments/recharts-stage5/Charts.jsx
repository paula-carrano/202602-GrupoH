import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, PieChart, Pie, Cell } from 'recharts'
import { number, dateLabel } from './fixtures'

const colors = ['#123b70', '#a13e00']
const SeriesTooltip = ({ active, payload }) => {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  return <div className="lab-tooltip" role="status">
    <strong>{dateLabel(row.date)}</strong><p>Estrategia: {row.strategy}</p>
    <p>Patrimonio: {number(row.wealth)} créditos</p><p>Saldo: {number(row.balance)} créditos</p>
  </div>
}
export const Charts = ({ series, positions }) => {
  const hasSeries = series.some(p => Number.isFinite(p.wealth) || Number.isFinite(p.balance))
  const hasDistribution = positions.length > 0 && positions.every(p => Number.isFinite(p.value) && p.value >= 0) && positions.some(p => p.value > 0)
  return <div className="row g-3 mb-4">
    <section className="col-12 col-lg-6" aria-labelledby="line-title">
      <h2 id="line-title">Patrimonio y saldo — prueba</h2>
      <p id="line-help">Foco en el gráfico y flechas izquierda/derecha. Todos los valores están también en la tabla.</p>
      {hasSeries ? <div className="lab-chart" data-testid="line">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={series} accessibilityLayer aria-label="Patrimonio y saldo sintéticos" aria-describedby="line-help" margin={{ left: 0, right: 12, top: 12, bottom: 12 }}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="date" tickFormatter={dateLabel} tick={{ fontSize: 11 }} /><YAxis width={48} tickFormatter={number} />
            <Tooltip content={<SeriesTooltip />} filterNull={false} /><Legend />
            <Line type="linear" dataKey="wealth" name="Patrimonio (créditos)" stroke={colors[0]} strokeWidth={2} connectNulls={false} isAnimationActive={false} />
            <Line type="linear" dataKey="balance" name="Saldo (créditos)" stroke={colors[1]} strokeWidth={2} strokeDasharray="6 3" connectNulls={false} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div> : <p role="status">{series.length ? 'Valores de serie no disponibles.' : 'Sin serie.'}</p>}
    </section>
    <section className="col-12 col-lg-6" aria-labelledby="pie-title">
      <h2 id="pie-title">Distribución — prueba</h2>
      {hasDistribution ? <div className="lab-chart" data-testid="pie">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart accessibilityLayer aria-label="Distribución sintética; alternativa tabular a continuación">
            <Pie data={positions} dataKey="value" nameKey="name" innerRadius="45%" outerRadius="70%" isAnimationActive={false}>
              {positions.map((p, i) => <Cell key={p.name} fill={colors[i % colors.length]} />)}
            </Pie>
            <Tooltip formatter={value => [`${number(value)} créditos`, 'Valor']} /><Legend />
          </PieChart>
        </ResponsiveContainer>
      </div> : <p role="status">Distribución no representable: vacía, incompleta o total cero. Consultá la tabla; no se normalizan datos faltantes.</p>}
    </section>
  </div>
}
