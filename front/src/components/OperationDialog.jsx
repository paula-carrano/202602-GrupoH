import { useState } from "react";
import { useMarket } from "../hooks/useMarket";
import { useMutation } from "../hooks/useMutation";
import { useSessionRequest } from "../hooks/useSessionRequest";
import { useDialogFocus } from "../hooks/useDialogFocus";
import { getQuoteAtDate, placeOrder, topup } from "../services/market";
import { amount, decimal } from "../utils/financial";

export function OperationDialog({ side, playerId, onClose }) {
    const market = useMarket();
    const mutation = useMutation(market.onLogout);

    const [input, setInput] = useState("");
    const [confirmed, setConfirmed] = useState(false);

    const close = () => {
        if (!mutation.busy) onClose();
    };

    const ref = useDialogFocus(true, close);

    const quote = useSessionRequest({
        session: market.session,
        requestKey: ["order-price", playerId],
        enabled: side !== "topup",
        request: (options) =>
            getQuoteAtDate(
                playerId,
                new Date().toISOString().slice(0, 10),
                options,
            ),
        onUnauthorized: market.onLogout,
    });

    const position = market.view.positions.find((p) => p.playerId === playerId);

    // Corrección SonarCloud: se reemplaza el ternario anidado por switch.
    let title;

    switch (side) {
        case "topup":
            title = "Recargar créditos";
            break;
        case "buy":
            title = "Comprar tokens";
            break;
        default:
            title = "Vender tokens";
    }

    const quantity = Number(input);

    const valid =
        side === "topup"
            ? /^\d+(\.\d{1,4})?$/.test(input) &&
              decimal(input).gte("0.0001") &&
              decimal(input).lte(10000)
            : /^\d+$/.test(input) &&
              Number.isSafeInteger(quantity) &&
              quantity > 0 &&
              quantity <= 2147483647 &&
              (side !== "sell" || quantity <= (position?.tokenQuantity || 0));

    const ready =
        market.allowed &&
        !market.portfolio.loading &&
        !market.portfolio.error &&
        market.portfolio.data &&
        (side === "topup" || quote.data);

    const submit = async (event) => {
        event.preventDefault();

        if (
            !valid ||
            !ready ||
            !confirmed ||
            mutation.busy ||
            mutation.uncertain ||
            mutation.result
        ) {
            return;
        }

        const response = await mutation.run((options) =>
            side === "topup"
                ? topup(market.session.userId, input, options)
                : placeOrder(side, playerId, quantity, options),
        );

        if (response) market.refresh();
    };

    return (
        <>
            <div className="modal-backdrop fade show" />

            <dialog
                open
                ref={ref}
                tabIndex={-1}
                aria-modal="true"
                aria-labelledby="operation-title"
                className="modal d-block"
                onCancel={(event) => {
                    event.preventDefault();
                    close();
                }}
            >
                <div className="modal-dialog modal-dialog-centered">
                    <div className="modal-content">
                        <div className="modal-header">
                            <h2 id="operation-title" className="h5">
                                {title}
                            </h2>

                            {/* Corrección SonarCloud: se especifica type="button". */}
                            <button
                                type="button"
                                className="btn-close"
                                aria-label="Cerrar operación"
                                disabled={mutation.busy}
                                onClick={close}
                            />
                        </div>

                        <form onSubmit={submit}>
                            <div className="modal-body">
                                {side === "topup" ? (
                                    <p>
                                        Recarga simulada: de 0.0001 a 10000
                                        créditos.
                                    </p>
                                ) : (
                                    <p>
                                        {position?.playerLabel ||
                                            `Jugador ${playerId}`}
                                        {" · "}
                                        Tenencia: {position?.tokenQuantity ??
                                            0}{" "}
                                        tokens
                                    </p>
                                )}

                                <p>
                                    Saldo disponible:{" "}
                                    {market.portfolio.data?.creditsBalance ??
                                        "No disponible"}{" "}
                                    CRD
                                </p>

                                {/* Corrección SonarCloud: se utiliza output en lugar de role="status". */}
                                {quote.loading && (
                                    <output>Consultando cotización…</output>
                                )}

                                {quote.error && (
                                    <p role="alert">
                                        {quote.error}{" "}
                                        <button
                                            type="button"
                                            onClick={quote.retry}
                                        >
                                            Reintentar cotización
                                        </button>
                                    </p>
                                )}

                                {market.portfolio.error && (
                                    <p role="alert">
                                        {market.portfolio.error}{" "}
                                        <button
                                            type="button"
                                            onClick={market.portfolio.retry}
                                        >
                                            Reintentar saldo
                                        </button>
                                    </p>
                                )}

                                {!mutation.result && (
                                    <>
                                        <label className="form-label w-100">
                                            {side === "topup"
                                                ? "Monto en créditos"
                                                : "Cantidad de tokens"}

                                            <input
                                                className="form-control"
                                                inputMode={
                                                    side === "topup"
                                                        ? "decimal"
                                                        : "numeric"
                                                }
                                                value={input}
                                                onChange={(event) => {
                                                    setInput(
                                                        event.target.value,
                                                    );
                                                    setConfirmed(false);
                                                }}
                                                disabled={
                                                    mutation.busy ||
                                                    mutation.uncertain
                                                }
                                                required
                                            />
                                        </label>

                                        {input && !valid && (
                                            <p role="alert">
                                                {side === "sell"
                                                    ? "Ingresá una cantidad entera positiva que no supere tu tenencia."
                                                    : "Ingresá un valor válido dentro del rango permitido."}
                                            </p>
                                        )}

                                        {side !== "topup" && quote.data && (
                                            <p>
                                                Precio orientativo:{" "}
                                                {quote.data.price} CRD. Total
                                                estimado:{" "}
                                                {valid
                                                    ? amount(
                                                          decimal(
                                                              quote.data.price,
                                                          ).times(quantity),
                                                      )
                                                    : "—"}{" "}
                                                CRD. El precio final se confirma
                                                al ejecutar.
                                            </p>
                                        )}

                                        <label className="d-block">
                                            <input
                                                type="checkbox"
                                                checked={confirmed}
                                                onChange={(event) =>
                                                    setConfirmed(
                                                        event.target.checked,
                                                    )
                                                }
                                                disabled={
                                                    mutation.busy ||
                                                    mutation.uncertain
                                                }
                                            />{" "}
                                            Confirmo esta operación.
                                        </label>
                                    </>
                                )}

                                {mutation.error && (
                                    <p role="alert">{mutation.error}</p>
                                )}

                                {mutation.uncertain && (
                                    <>
                                        <p>
                                            Consultá Portfolio e Historial antes
                                            de iniciar otra operación. Esta
                                            solicitud no se reenviará.
                                        </p>

                                        <button
                                            type="button"
                                            className="btn btn-outline-primary"
                                            onClick={() => {
                                                market.refresh();
                                                onClose();
                                            }}
                                        >
                                            Actualizar datos y cerrar
                                        </button>
                                    </>
                                )}

                                {/* Corrección SonarCloud: se utiliza output en lugar de role="status". */}
                                {mutation.result && (
                                    <output className="d-block">
                                        <strong>Operación confirmada</strong>

                                        <p>
                                            Transacción #
                                            {mutation.result.transactionId}
                                            {" · "}
                                            Total:{" "}
                                            {mutation.result.totalAmount ??
                                                mutation.result
                                                    .amountCredited}{" "}
                                            CRD
                                            {" · "}
                                            Saldo:{" "}
                                            {mutation.result.balanceAfter} CRD
                                        </p>

                                        {mutation.result.unitPrice != null && (
                                            <p>
                                                Precio ejecutado:{" "}
                                                {mutation.result.unitPrice} CRD
                                                {" · "}
                                                Tokens:{" "}
                                                {mutation.result.tokensAfter}
                                            </p>
                                        )}
                                    </output>
                                )}
                            </div>

                            <div className="modal-footer">
                                <button
                                    type="button"
                                    className="btn btn-secondary"
                                    onClick={close}
                                    disabled={mutation.busy}
                                >
                                    Cerrar
                                </button>

                                {!mutation.result && (
                                    <button
                                        type="submit"
                                        className="btn btn-primary"
                                        disabled={
                                            !valid ||
                                            !ready ||
                                            !confirmed ||
                                            mutation.busy ||
                                            mutation.uncertain
                                        }
                                    >
                                        {mutation.busy
                                            ? "Procesando…"
                                            : "Confirmar operación"}
                                    </button>
                                )}
                            </div>
                        </form>
                    </div>
                </div>
            </dialog>
        </>
    );
}
