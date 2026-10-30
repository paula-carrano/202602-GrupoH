import { useEffect, useMemo, useRef, useState } from "react";
import { MarketContext } from "../hooks/useMarket";
import { useSessionRequest } from "../hooks/useSessionRequest";
import { hasSessionIdentity, subscribeSession } from "../services/session";
import { getPortfolio, getAlerts } from "../services/market";
import { connectAlerts } from "../services/alertStream";
import { portfolioView } from "../utils/financial";
import { PlayerProfileDialog } from "./PlayerProfileDialog";
import { OperationDialog } from "./OperationDialog";

export function MarketProvider({ session, onLogout, children }) {
    const [revision, setRevision] = useState(0);
    const [alertRevision, setAlertRevision] = useState(0);
    const [selected, setSelected] = useState(null);
    const [operation, setOperation] = useState(null);
    const [notifications, setNotifications] = useState([]);
    const [streamStatus, setStreamStatus] = useState("");

    const seen = useRef(new Set());

    const allowed = hasSessionIdentity(session);

    const portfolio = useSessionRequest({
        session,
        personal: true,
        requestKey: ["portfolio", revision],
        request: (options) => getPortfolio(session.userId, options),
        onUnauthorized: onLogout,
    });

    const alerts = useSessionRequest({
        session,
        personal: true,
        requestKey: ["alerts", alertRevision],
        request: getAlerts,
        onUnauthorized: onLogout,
    });

    useEffect(() => {
        if (!allowed) return;

        const stop = connectAlerts({
            token: session.token,
            onStatus: setStreamStatus,
            onUnauthorized: onLogout,

            onConnected: () => setAlertRevision((v) => v + 1),

            onEvent: (event) => {
                if (!event.alertId || !event.playerId) return;

                const key = `${event.alertId}:${event.triggeredAt}`;

                if (seen.current.has(key)) return;

                seen.current.add(key);

                setNotifications((items) => [event, ...items].slice(0, 10));
                setAlertRevision((v) => v + 1);
                setRevision((v) => v + 1);
            },
        });

        const unsubscribe = subscribeSession(stop);

        return () => {
            stop();
            unsubscribe();
        };
    }, [session.token, allowed, onLogout]);

    // Corrección SonarCloud S6481:
    // El objeto del contexto conserva su referencia mientras
    // sus dependencias no cambien.
    const value = useMemo(
        () => ({
            session,
            onLogout,
            allowed,
            portfolio,
            view: portfolioView(portfolio.data),
            alerts,
            revision,
            streamStatus,

            refresh: () => {
                setRevision((v) => v + 1);
                setAlertRevision((v) => v + 1);
            },

            refreshAlerts: () => setAlertRevision((v) => v + 1),

            selectPlayer: (id) => setSelected({ id }),

            openOrder: (side, playerId) => {
                setSelected(null);
                setOperation({ side, playerId });
            },

            openTopup: () => setOperation({ side: "topup" }),
        }),
        [session, onLogout, allowed, portfolio, alerts, revision, streamStatus],
    );

    return (
        <MarketContext.Provider value={value}>
            {notifications.length > 0 && (
                <section
                    aria-label="Notificaciones de precios"
                    className="mb-3"
                >
                    {notifications.map((n) => (
                        <output
                            className="alert alert-info d-block"
                            key={`${n.alertId}:${n.triggeredAt}`}
                        >
                            <strong>{n.playerName}</strong>: cotización{" "}
                            {n.currentPrice} CRD (objetivo {n.targetPrice}).
                            <button
                                type="button"
                                className="btn btn-sm btn-primary ms-2"
                                onClick={() =>
                                    value.openOrder("buy", n.playerId)
                                }
                            >
                                Comprar
                            </button>
                            <button
                                type="button"
                                className="btn btn-sm btn-outline-primary ms-2"
                                onClick={() =>
                                    value.openOrder("sell", n.playerId)
                                }
                            >
                                Vender
                            </button>
                            <button
                                type="button"
                                className="btn btn-sm ms-2"
                                onClick={() =>
                                    setNotifications((items) =>
                                        items.filter((item) => item !== n),
                                    )
                                }
                            >
                                Descartar aviso
                            </button>
                        </output>
                    ))}
                </section>
            )}

            {children}

            <PlayerProfileDialog
                player={selected}
                session={session}
                onUnauthorized={onLogout}
                onClose={() => setSelected(null)}
            />

            {operation && (
                <OperationDialog
                    key={`${operation.side}:${operation.playerId}`}
                    {...operation}
                    onClose={() => setOperation(null)}
                />
            )}
        </MarketContext.Provider>
    );
}
