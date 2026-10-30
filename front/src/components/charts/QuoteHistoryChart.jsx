import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { FinancialPanel, FinancialTable } from '../FinancialPanel'

export const QuoteHistoryChart = ({ state = 'unavailable', points = [], playerLabel, periods = [], period = '', onPeriodChange, onDateChange, date = '', ...feedback }) => <FinancialPanel title="Evolución histórica de cotizaciones" description={`Jugador: ${playerLabel || 'Sin selección'}`} state={state === 'ready' && points.length === 0 ? 'empty' : state} {...feedback}>
  <p>Fecha · Cotización en créditos · Versión de estrategia</p>
  <div style={{ height: 280, width: '100%', minWidth: 0 }} aria-label="Gráfico de cotizaciones">
    <ResponsiveContainer width="100%" height="100%"><LineChart data={points} accessibilityLayer><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="date" tickFormatter={value => value.slice(0, 10)} /><YAxis width={70} /><Tooltip /><Legend /><Line dataKey="value" name="Cotización (CRD)" stroke="#1957ad" type="stepAfter" connectNulls={false} isAnimationActive={false} /></LineChart></ResponsiveContainer>
  </div>
  {periods.length > 0 && onPeriodChange && <label>Período<select className="form-select" value={period} onChange={event => onPeriodChange(event.target.value)}>{periods.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>}
  {onDateChange && <label>Cotización a fecha<input type="date" className="form-control" value={date} onChange={event => onDateChange(event.target.value)} /></label>}
  {points.length === 1 && <p role="status">Hay un único punto disponible; no se infiere una evolución.</p>}
  <FinancialTable caption="Cotizaciones disponibles" columns={[{ key: 'date', label: 'Fecha' }, { key: 'value', label: 'Cotización (créditos)' }, { key: 'strategyVersion', label: 'Versión de estrategia' }]} rows={points} />
</FinancialPanel>
