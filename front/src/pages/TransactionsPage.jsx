import { FinancialPanel, FinancialTable, financialValue } from "../components";

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
                    },
                    {
                        key: "total",
                        label: "Total (créditos)",
                    },
                ]}
            />
        </FinancialPanel>
    </>
);
