import { useId } from 'react'

export const financialValue = value => value == null ? 'No disponible' : value

// Estados y valores de presentación; no DTOs ni cálculos de valuación.
export const FinancialPanel = ({ title, description, state = 'unavailable', error, onRetry, emptyText = 'Todavía no hay datos para mostrar.', children }) => {
  const id = useId()
  const showData = state === 'ready' || state === 'partial'
  return <section className="card h-100 financial-panel" aria-labelledby={id} aria-busy={state === 'loading'}>
    <div className="card-body">
      <h2 id={id} className="h5">{title}</h2>
      {description && <p>{description}</p>}
      {state === 'loading' && <p role="status">Cargando datos…</p>}
      {state === 'unavailable' && <p role="status">Datos no disponibles. Funcionalidad pendiente de integración.</p>}
      {state === 'forbidden' && <p role="alert">No tenés permiso para consultar esta información.</p>}
      {state === 'empty' && <p role="status">{emptyText}</p>}
      {state === 'error' && <div role="alert"><p>{error || 'No se pudieron cargar los datos.'}</p>{onRetry && <button className="btn btn-outline-primary" onClick={onRetry}>Reintentar</button>}</div>}
      {state === 'partial' && <p role="status">Información parcial. Los valores desconocidos se muestran como no disponibles.</p>}
      {showData && children}
    </div>
  </section>
}

export const FinancialTable = ({ caption, columns, rows }) => <div className="table-responsive" tabIndex={0} role="region" aria-label={caption}>
  <table className="table mb-0">
    <caption className="caption-top">{caption}</caption>
    <thead><tr>{columns.map(column => <th scope="col" key={column.key}>{column.label}</th>)}</tr></thead>
    <tbody>{rows.map((row, index) => <tr key={row.id ?? index}>{columns.map(column => <td key={column.key}>{column.render ? column.render(row) : financialValue(row[column.key])}</td>)}</tr>)}</tbody>
  </table>
</div>
