import { FinancialPanel, FinancialTable } from '../FinancialPanel'

export const WealthHistoryChart = ({ state = 'unavailable', points = [], ...feedback }) => <FinancialPanel title="Evolución del patrimonio y saldo" state={state === 'ready' && points.length === 0 ? 'empty' : state} {...feedback}>
  <p>Fecha · Patrimonio total (créditos) · Saldo disponible (créditos)</p>
  <p>Visualización interactiva pendiente. No se reconstruye el histórico desde operaciones.</p>
  {points.length === 1 && <p role="status">Hay un único punto disponible; no se infiere una evolución.</p>}
  <FinancialTable caption="Histórico patrimonial disponible" columns={[{ key: 'date', label: 'Fecha' }, { key: 'totalWealth', label: 'Patrimonio total (créditos)' }, { key: 'availableBalance', label: 'Saldo disponible (créditos)' }]} rows={points} />
</FinancialPanel>
