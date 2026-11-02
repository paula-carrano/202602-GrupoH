import { useState } from 'react'
import { Charts } from '@lab-renderer'
import { cases, number, dateLabel } from './fixtures'
import './lab.css'

export const Lab = () => {
  const [selected, setSelected] = useState('complete')
  const data = cases[selected]
  return <main className="lab container py-3">
    <h1>Recharts — SOLO PRUEBA TÉCNICA</h1>
    <p>Datos sintéticos y esquema hipotético. Sin servicios, sesiones ni contratos financieros reales. Unidad de prueba: créditos.</p>
    <label htmlFor="case">Escenario</label>
    <select id="case" className="form-select mb-3" value={selected} onChange={e => setSelected(e.target.value)}>
      {Object.entries(cases).map(([id, item]) => <option value={id} key={id}>{item.label}</option>)}
    </select>
    <p role="status">Escenario de prueba: {data.label}. {data.series.length === 0 ? 'Sin datos.' : data.series.length === 1 ? 'Un único punto; no se infiere evolución.' : 'Valores faltantes: no se unen ni reemplazan por cero.'}</p>
    <Charts key={selected} {...data} />
    <section aria-labelledby="series-title">
      <h2 id="series-title">Alternativa tabular — series</h2>
      <div className="table-responsive" tabIndex={0} role="region" aria-label="Series de prueba">
        <table className="table"><caption>Series sintéticas; mismos registros que el gráfico</caption>
          <thead><tr>{['Fecha civil', 'Estrategia simulada', 'Patrimonio (créditos)', 'Saldo (créditos)'].map(s => <th scope="col" key={s}>{s}</th>)}</tr></thead>
          <tbody>{data.series.map(p => <tr key={p.date}><th scope="row">{dateLabel(p.date)}</th><td>{p.strategy}</td><td>{number(p.wealth)}</td><td>{number(p.balance)}</td></tr>)}</tbody>
        </table>
      </div>
    </section>
    <section aria-labelledby="distribution-title">
      <h2 id="distribution-title">Alternativa tabular — distribución</h2>
      <div className="table-responsive" tabIndex={0} role="region" aria-label="Distribución de prueba">
        <table className="table"><caption>Posiciones sintéticas; valores sin calcular PnL ni porcentajes</caption>
          <thead><tr><th scope="col">Jugador</th><th scope="col">Valor (créditos)</th></tr></thead>
          <tbody>{data.positions.map(p => <tr key={p.name}><th scope="row">{p.name}</th><td>{number(p.value)}</td></tr>)}</tbody>
        </table>
      </div>
    </section>
  </main>
}
