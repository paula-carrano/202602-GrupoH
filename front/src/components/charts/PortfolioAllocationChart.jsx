import { Tooltip, Legend, ResponsiveContainer, PieChart, Pie } from 'recharts'
import { FinancialPanel, FinancialTable } from '../FinancialPanel'

export const PortfolioAllocationChart = ({ state = 'unavailable', positions = [], ...feedback }) => <FinancialPanel title="Distribución de la cartera" state={state === 'ready' && positions.length === 0 ? 'empty' : state} emptyText="No tenés posiciones en cartera. Explorá el catálogo de jugadores." {...feedback}>
  <p>Valor actual en créditos y porcentaje informado por jugador.</p>
  <div style={{ height: 300, width: '100%', minWidth: 0 }} aria-label="Gráfico de distribución de cartera">
    <ResponsiveContainer width="100%" height="100%"><PieChart accessibilityLayer><Pie data={positions.map((p, i) => ({ ...p, fill: ['#18c9bd', '#53cc91', '#ffb64d', '#9276df', '#f4d35e', '#55b8df'][i % 6] }))} dataKey="currentValue" nameKey="playerLabel" innerRadius="45%" outerRadius="70%" isAnimationActive={false} /><Tooltip /><Legend /></PieChart></ResponsiveContainer>
  </div>
  <FinancialTable caption="Distribución disponible" columns={[{ key: 'playerLabel', label: 'Jugador' }, { key: 'currentValue', label: 'Valor actual (créditos)' }, { key: 'percentage', label: 'Porcentaje (%)' }]} rows={positions} />
</FinancialPanel>
