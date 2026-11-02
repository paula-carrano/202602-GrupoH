// SOLO PRUEBA TÉCNICA. Esquema hipotético de presentación, NO contrato backend.
const series = [
  { date: '2026-10-01', strategy: 'SIMULADA-v1', wealth: 85, balance: 50 },
  { date: '2026-10-02', strategy: 'SIMULADA-v1', wealth: 90.1234, balance: 40 },
  { date: '2026-10-03', strategy: 'SIMULADA-v2', wealth: 100, balance: 0 },
]
const positions = [{ name: 'Jugador A — PRUEBA', value: 60 }, { name: 'Jugador B — PRUEBA', value: 40 }]
export const cases = {
  complete: { label: 'Completo', series, positions },
  empty: { label: 'Vacío', series: [], positions: [] },
  nulls: { label: 'Nulos', series: series.map(p => ({ ...p, wealth: null, balance: null })), positions: positions.map(p => ({ ...p, value: null })) },
  incomplete: { label: 'Incompleto', series: series.map((p, i) => i === 1 ? { ...p, wealth: null, balance: undefined } : p), positions: [{ ...positions[0] }, { ...positions[1], value: null }] },
  zero: { label: 'Cero', series: series.map(p => ({ ...p, wealth: 0, balance: 0 })), positions: positions.map(p => ({ ...p, value: 0 })) },
  single: { label: 'Punto único', series: series.slice(0, 1), positions: positions.slice(0, 1) },
}
export const number = value => value == null ? 'No disponible' : new Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 }).format(value)
// Fechas civiles del fixture; no conversión por zona del equipo.
export const dateLabel = date => date.split('-').reverse().join('/')
