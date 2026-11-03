import { decimal } from "../utils/financial";
import { FinancialPanel, FinancialTable, financialValue } from "../components";

const money = value => value == null ? "No disponible" : decimal(value).toFixed(4);

export const TransactionsPage = ({
    state = "unavailable",
    transactions = [],
    onSelectPlayer,
    ...feedback
}) => (
    <>
        <h1 className="h5">Historial</h1>

        {state === "unavailable" && (
            <output className="d-block">
                Esta sección todavía no está disponible.
            </output>
        )}

        <FinancialPanel
            title="Historial de operaciones"
            state={state === "ready" && !transactions.length ? "empty" : state}
            emptyText="Todavía no hay operaciones."
            {...feedback}
        >
            <FinancialTable
                caption="Operaciones disponibles"
                rows={transactions}
                columns={[
                    {
                        key: "dateLabel",
                        label: "Fecha y hora",
                    },
                    {
                        key: "type",
                        label: "Operación",
                        render: (row) => (
                            <span className="badge text-bg-secondary">
                                {financialValue(row.type)}
                            </span>
                        ),
                    },
                    {
                        key: "playerLabel",
                        label: "Jugador",
                        render: (row) =>
                            onSelectPlayer && row.playerId != null ? (
                                <button
                                    type="button"
                                    className="btn btn-link"
                                    onClick={() => onSelectPlayer(row.playerId)}
                                >
                                    {row.playerLabel}
                                </button>
                            ) : (
                                financialValue(row.playerLabel)
                            ),
                    },
                    {
                        key: "quantity",
                        label: "Tokens",
                    },
                    {
                        key: "unitPrice",
                        label: "Precio unitario (créditos)",
                        render: row => money(row.unitPrice),
                    },
                    {
                        key: "total",
                        label: "Total (créditos)",
                        render: row => money(row.total),
                    },
                    {
                        key: "audit",
                        label: "Trazabilidad",
                        render: row => <details className="transaction-audit">
                            <summary>Ver detalle de operación {row.id}</summary>
                            <dl className="mt-2 mb-0">
                                <dt>ID de operación</dt><dd>{financialValue(row.id)}</dd>
                                <dt>Saldo anterior (CRD)</dt><dd>{money(row.balanceBefore)}</dd>
                                <dt>Saldo posterior (CRD)</dt><dd>{money(row.balanceAfter)}</dd>
                                <dt>Tokens anteriores</dt><dd>{row.type === 'RECARGA' ? 'No aplica' : financialValue(row.tokensBefore)}</dd>
                                <dt>Tokens posteriores</dt><dd>{row.type === 'RECARGA' ? 'No aplica' : financialValue(row.tokensAfter)}</dd>
                                <dt>Versión de estrategia</dt><dd>{financialValue(row.strategyVersion)}</dd>
                            </dl>
                        </details>,
                    },
                ]}
            />
        </FinancialPanel>
    </>
);
