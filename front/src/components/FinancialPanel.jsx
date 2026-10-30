import { useId } from "react";

export const financialValue = (value) =>
    value == null ? "No disponible" : value;

// Estados y valores de presentación; no DTOs ni cálculos de valuación.
export const FinancialPanel = ({
    title,
    description,
    state = "unavailable",
    error,
    onRetry,
    emptyText = "Todavía no hay datos para mostrar.",
    children,
}) => {
    const id = useId();
    const showData = state === "ready" || state === "partial";

    return (
        <section
            className="card h-100 financial-panel"
            aria-labelledby={id}
            aria-busy={state === "loading"}
        >
            <div className="card-body">
                <h2 id={id} className="h5">
                    {title}
                </h2>

                {description && <p>{description}</p>}

                {state === "loading" && <output>Cargando datos…</output>}

                {state === "unavailable" && (
                    <output>
                        Datos no disponibles. Funcionalidad pendiente de
                        integración.
                    </output>
                )}

                {state === "forbidden" && (
                    <p role="alert">
                        No tenés permiso para consultar esta información.
                    </p>
                )}

                {state === "empty" && <output>{emptyText}</output>}

                {state === "error" && (
                    <div role="alert">
                        <p>{error || "No se pudieron cargar los datos."}</p>

                        {onRetry && (
                            <button
                                type="button"
                                className="btn btn-outline-primary"
                                onClick={onRetry}
                            >
                                Reintentar
                            </button>
                        )}
                    </div>
                )}

                {state === "partial" && (
                    <output>
                        Información parcial. Los valores desconocidos se
                        muestran como no disponibles.
                    </output>
                )}

                {showData && children}
            </div>
        </section>
    );
};

export const FinancialTable = ({ caption, columns = [], rows = [] }) => (
    <div className="table-responsive">
        <table className="table">
            <caption>{caption}</caption>

            <thead>
                <tr>
                    {columns.map((column) => (
                        <th key={column.key} scope="col">
                            {column.label}
                        </th>
                    ))}
                </tr>
            </thead>

            <tbody>
                {rows.map((row, index) => (
                    <tr key={row.playerId ?? index}>
                        {columns.map((column) => (
                            <td key={column.key}>
                                {column.render
                                    ? column.render(row)
                                    : financialValue(row[column.key])}
                            </td>
                        ))}
                    </tr>
                ))}
            </tbody>
        </table>
    </div>
);
