import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { FinancialPanel, FinancialTable } from '../FinancialPanel'

export const WealthHistoryChart = ({ state = 'unavailable', points = [], ...feedback }) => <FinancialPanel title="Evolución del patrimonio y saldo" state={state === 'ready' && points.length === 0 ? 'empty' : state} {...feedback}>
  <p>Fecha · Patrimonio total (créditos) · Saldo disponible (créditos)</p>
  <div style={{ height: 280, width: '100%', minWidth: 0 }} aria-label="Gráfico de patrimonio y saldo">
    <ResponsiveContainer width="100%" height="100%"><LineChart data={points} accessibilityLayer><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="date" /><YAxis width={70} /><Tooltip /><Legend /><Line dataKey="totalWealth" name="Patrimonio (CRD)" stroke="#1957ad" connectNulls={false} isAnimationActive={false} /><Line dataKey="availableBalance" name="Saldo (CRD)" stroke="#218559" connectNulls={false} isAnimationActive={false} /></LineChart></ResponsiveContainer>
  </div>
  {points.length === 1 && <p role="status">Hay un único punto disponible; no se infiere una evolución.</p>}
  <FinancialTable caption="Histórico patrimonial disponible" columns={[{ key: 'date', label: 'Fecha' }, { key: 'totalWealth', label: 'Patrimonio total (créditos)' }, { key: 'availableBalance', label: 'Saldo disponible (créditos)' }]} rows={points} />
</FinancialPanel>
