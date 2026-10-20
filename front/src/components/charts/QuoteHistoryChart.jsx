import { FinancialPanel, FinancialTable } from '../FinancialPanel'

export const QuoteHistoryChart = ({ state = 'unavailable', points = [], playerLabel, periods = [], period = '', onPeriodChange, onDateChange, date = '', ...feedback }) => <FinancialPanel title="Evolución histórica de cotizaciones" description={`Jugador: ${playerLabel || 'Sin selección'}`} state={state === 'ready' && points.length === 0 ? 'empty' : state} {...feedback}>
  <p>Fecha · Cotización en créditos · Versión de estrategia</p>
  <p>Visualización interactiva pendiente. Datos disponibles en la tabla.</p>
  {periods.length > 0 && onPeriodChange && <label>Período<select className="form-select" value={period} onChange={event => onPeriodChange(event.target.value)}>{periods.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>}
  {onDateChange && <label>Cotización a fecha<input type="date" className="form-control" value={date} onChange={event => onDateChange(event.target.value)} /></label>}
  {points.length === 1 && <p role="status">Hay un único punto disponible; no se infiere una evolución.</p>}
  <FinancialTable caption="Cotizaciones disponibles" columns={[{ key: 'date', label: 'Fecha' }, { key: 'value', label: 'Cotización (créditos)' }, { key: 'strategyVersion', label: 'Versión de estrategia' }]} rows={points} />
</FinancialPanel>
