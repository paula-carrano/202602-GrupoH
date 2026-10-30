import { FinancialPanel, FinancialTable, financialValue } from "../components";

export const RankingPage = ({
    state = "unavailable",
    players = [],
    strategy,
    onSelectPlayer,
    onBuy,
    ...feedback
}) => (
    <>
        <h1 className="h5">Ranking</h1>

        {state === "unavailable" && (
            <output className="d-block">
                Esta sección todavía no está disponible.
            </output>
        )}

        <FinancialPanel
            title="Ranking por estrategia de valoración"
            state={state === "ready" && !players.length ? "empty" : state}
            {...feedback}
        >
            <p>Estrategia activa: {financialValue(strategy)}</p>

            <FinancialTable
                caption="Clasificación recibida"
                rows={players}
                columns={[
                    {
                        key: "rank",
                        label: "Posición informada",
                    },
                    {
                        key: "playerLabel",
                        label: "Jugador",
                        render: (row) =>
                            onSelectPlayer ? (
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
                        key: "marketQuote",
                        label: "Cotización (créditos)",
                    },
                    {
                        key: "buy",
                        label: "Operación",
                        render: (row) => (
                            <button
                                type="button"
                                className="btn btn-outline-primary"
                                disabled={!onBuy}
                                onClick={() => onBuy(row.playerId)}
                            >
                                Comprar
                            </button>
                        ),
                    },
                ]}
            />
        </FinancialPanel>
    </>
);
