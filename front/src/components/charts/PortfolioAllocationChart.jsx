import { FinancialPanel, FinancialTable } from '../FinancialPanel'

export const PortfolioAllocationChart = ({ state = 'unavailable', positions = [], ...feedback }) => <FinancialPanel title="Distribución de la cartera" state={state === 'ready' && positions.length === 0 ? 'empty' : state} emptyText="No tenés posiciones en cartera. Explorá el catálogo de jugadores." {...feedback}>
  <p>Valor actual en créditos y porcentaje informado por jugador.</p>
  <p>Gráfico de dona pendiente. Datos disponibles en la tabla.</p>
  <FinancialTable caption="Distribución disponible" columns={[{ key: 'playerLabel', label: 'Jugador' }, { key: 'currentValue', label: 'Valor actual (créditos)' }, { key: 'percentage', label: 'Porcentaje (%)' }]} rows={positions} />
</FinancialPanel>
