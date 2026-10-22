import { useSessionRequest } from "../hooks/useSessionRequest";
import { getPlayer } from "../services/players";
import { useDialogFocus } from '../hooks/useDialogFocus';
import { PlayerInvestmentSummary } from './PlayerInvestmentSummary';
import { QuoteHistoryChart } from './charts/QuoteHistoryChart';
import { financialValue } from './FinancialPanel';

const metrics = [
    { key: "goals", label: "Goles" },
    { key: "assists", label: "Asistencias" },
    { key: "yellowCards", label: "Tarjetas amarillas", marker: "yellow" },
    { key: "redCards", label: "Tarjetas rojas", marker: "red" },
    { key: "minutesPlayed", label: "Minutos jugados" },
];

export const PlayerProfileDialog = ({ player, onClose, session, onUnauthorized, investment, marketQuote, quotes }) => {
    const dialogRef = useDialogFocus(Boolean(player), onClose);
    const { data: detail, loading, error, retry } = useSessionRequest({
        session,
        requestKey: player?.id,
        enabled: Boolean(player),
        request: ({ signal }) => getPlayer(player.id, { signal }),
        onUnauthorized,
    });

    if (!player) return null;

    const valueOrPending = (value) => value ?? "—";
    const profile = detail || player;
    const stats = detail?.statistics;

    return (
        <>
            <div className="modal-backdrop fade show" />

            <dialog
                ref={dialogRef}
                tabIndex={-1}
                aria-modal="true"
                open
                className="modal fade show d-block player-profile-modal"
                aria-labelledby="player-profile-title"
                onCancel={onClose}
            >
                <div className="modal-dialog modal-dialog-centered modal-lg">
                    <article className="modal-content player-profile-card">
                        <header className="player-profile-header">
                            <div>
                                <p className="player-profile-eyebrow mb-1">
                                    Perfil del jugador
                                </p>

                                <h2
                                    id="player-profile-title"
                                    className="player-profile-name mb-1"
                                >
                                    {profile.firstName} {profile.lastName}
                                </h2>

                                <p className="player-profile-team mb-0">
                                    {profile.currentTeam || "—"}
                                </p>
                            </div>

                            <button
                                type="button"
                                className="btn btn-outline-light"
                                onClick={onClose}
                            >
                                Cerrar
                            </button>
                        </header>

                        <section aria-label="Mercado">
                        <h3 className="h5 px-4">Mercado</h3>
                        <p className="px-4">Cotización actual (créditos): {financialValue(marketQuote)}</p>
                        <div className="player-profile-metrics">
                            <section className="player-profile-metric">
                                <span>Liga</span>
                                <strong>{profile.league || "—"}</strong>
                            </section>

                            {metrics.map((metric) => (
                                <section
                                    className="player-profile-metric"
                                    key={metric.key}
                                >
                                    <span className="d-flex align-items-center gap-2">
                                        {metric.marker && (
                                            <i
                                                className={`player-card-marker player-card-marker--${metric.marker}`}
                                                aria-hidden="true"
                                            />
                                        )}
                                        {metric.label}
                                    </span>

                                    <strong>
                                        {valueOrPending(stats?.[metric.key])}
                                    </strong>
                                </section>
                            ))}
                        </div>

                        <div className="px-3 pb-3"><QuoteHistoryChart playerLabel={`${profile.firstName} ${profile.lastName}`} {...quotes} /></div>
                        </section>
                        {loading && (
                            <output className="player-profile-status mb-0">
                                <span
                                    className="spinner-border spinner-border-sm me-2"
                                    aria-hidden="true"
                                />{" "}
                                Cargando perfil…
                            </output>
                        )}

                        {error && (
                            <output className="player-profile-status player-profile-status--error mb-0">
                                {error}
                                <button className="btn btn-outline-light ms-2" onClick={retry}>Reintentar perfil</button>
                            </output>
                        )}

                        {!loading && !error && !stats && (
                            <output className="player-profile-status mb-0">
                                Estadísticas todavía no disponibles.
                            </output>
                        )}
                        <div className="p-3"><PlayerInvestmentSummary {...investment} /></div>
                    </article>
                </div>
            </dialog>
        </>
    );
};
