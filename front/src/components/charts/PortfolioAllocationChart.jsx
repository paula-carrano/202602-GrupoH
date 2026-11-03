import { useMemo, useState } from 'react'
import { Tooltip, ResponsiveContainer, PieChart, Pie } from 'recharts'
import { FinancialPanel, FinancialTable } from '../FinancialPanel'
import { Pagination } from '../Pagination'
import { decimal } from '../../utils/financial'

const colors = ['#18c9bd', '#53cc91', '#ffb64d', '#9276df', '#f4d35e', '#55b8df']

export function allocationPresentation(positions) {
  const sorted = [...positions].sort((a, b) => decimal(b.currentValue).comparedTo(a.currentValue) || a.playerId - b.playerId)
  const total = sorted.reduce((sum, p) => sum.plus(p.currentValue), decimal(0))
  const percentage = value => total.isZero() ? 0 : decimal(value).div(total).times(100).toDecimalPlaces(2).toNumber()
  const rows = sorted.map(p => ({ ...p, percentage: percentage(p.currentValue) }))
  const sectors = rows.slice(0, 10)
  if (rows.length > 10) {
    const value = rows.slice(10).reduce((sum, p) => sum.plus(p.currentValue), decimal(0))
    sectors.push({ playerId: 'others', playerLabel: 'Otros', currentValue: value.toNumber(), percentage: percentage(value) })
  }
  return { rows, sectors: sectors.map((p, i) => ({ ...p, fill: colors[i % colors.length] })), total }
}

export function PortfolioAllocationChart({ state = 'unavailable', positions = [], ...feedback }) {
  const [page, setPage] = useState(1)
  const { rows, sectors, total } = useMemo(() => allocationPresentation(positions), [positions])
  const currentPage = Math.min(page, Math.max(1, Math.ceil(rows.length / 10)))
  return <FinancialPanel title="Distribución de la cartera" state={state === 'ready' && !rows.length ? 'empty' : state} emptyText="No tenés posiciones en cartera. Explorá el catálogo de jugadores." {...feedback}>
    <p>Valor actual en créditos y porcentaje del total de la cartera.</p>
    {total.isZero() ? <p>No hay valor de cartera para representar.</p> : <>
      <div style={{ height: 300, width: '100%', minWidth: 0 }} aria-label="Gráfico de distribución de cartera">
        <ResponsiveContainer width="100%" height="100%"><PieChart accessibilityLayer><Pie data={sectors} dataKey="currentValue" nameKey="playerLabel" innerRadius="45%" outerRadius="70%" isAnimationActive={false} /><Tooltip /></PieChart></ResponsiveContainer>
      </div>
      <ul className="allocation-legend" aria-label="Leyenda de distribución">
        {sectors.map(p => <li key={p.playerId}><span style={{ backgroundColor: p.fill }} aria-hidden="true" />{p.playerLabel}: {p.percentage}%</li>)}
      </ul>
    </>}
    <FinancialTable caption="Distribución disponible" columns={[{ key: 'playerLabel', label: 'Jugador' }, { key: 'currentValue', label: 'Valor actual (créditos)' }, { key: 'percentage', label: 'Porcentaje (%)' }]} rows={rows.slice((currentPage - 1) * 10, currentPage * 10)} />
    <Pagination page={currentPage} pageSize={10} total={rows.length} onPageChange={setPage} itemLabel="posiciones" />
  </FinancialPanel>
}
