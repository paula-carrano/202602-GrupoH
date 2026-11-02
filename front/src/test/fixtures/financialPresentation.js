// SOLO PRUEBA: ejemplos de presentación, no respuestas HTTP ni DTOs de backend.
// No importar desde módulos productivos ni derivar reglas financieras de estos valores.
export const financialPresentation = {
    empty: {
        label: "SOLO PRUEBA — cartera vacía",
        state: "empty",
        positions: [],
    },
    unknown: {
        label: "SOLO PRUEBA — dato desconocido",
        state: "unavailable",
        availableBalance: null,
        currentPortfolioValue: null,
        totalWealth: null,
    },
    error: {
        label: "SOLO PRUEBA — error de presentación",
        state: "error",
        message: "No se pudo cargar la información de tu inversión.",
    },
    singlePoint: {
        label: "SOLO PRUEBA — punto único",
        state: "ready",
        playerLabel: "Jugador de prueba",
        points: [{ date: "2026-10-01", value: 3.5 }],
    },
    marketAndPosition: {
        label: "SOLO PRUEBA — mercado e inversión personal",
        state: "ready",
        playerLabel: "Jugador de prueba",
        marketQuote: 3.5,
        position: {
            tokenQuantity: 10,
            averagePurchasePrice: 2,
            currentValue: 35,
            profitOrLoss: 15,
        },
    },
};
