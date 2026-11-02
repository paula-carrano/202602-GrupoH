import { playerName, initials } from "../utils/playerDisplay";

export const SelectedPlayerCard = ({
    selected,
    canBuy,
    canSell,
    onBuy,
    onSell,
    onShowDetail,
}) => (
    <section
        className="card p-3 p-md-4 mb-3"
        aria-label="Jugador seleccionado"
    >
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
            <div className="d-flex align-items-center gap-3">
                <span
                    className="player-avatar player-avatar-large rounded-circle d-inline-flex align-items-center justify-content-center flex-shrink-0"
                    aria-hidden="true"
                >
                    {selected ? initials(selected) : "FM"}
                </span>
                <div>
                    <p className="small text-body-secondary mb-1">
                        Panel de inversión
                    </p>
                    <h2 className="h4 mb-1">
                        {selected
                            ? playerName(selected)
                            : "Tu mercado de jugadores"}
                    </h2>
                    <p className="small text-body-secondary mb-0">
                        {selected
                            ? `${selected.currentTeam || "Equipo no disponible"} · ${selected.league || "Liga no disponible"}`
                            : "Seleccioná un jugador para consultar sus cotizaciones."}
                    </p>
                </div>
            </div>
            <div className="d-flex flex-wrap gap-2">
                <button
                    className="btn btn-primary"
                    disabled={!canBuy}
                    onClick={onBuy}
                >
                    Comprar tokens
                </button>
                <button
                    className="btn btn-outline-danger"
                    disabled={!canSell}
                    onClick={onSell}
                >
                    Vender tokens
                </button>
            </div>
        </div>
        {selected && (
            <>
                <dl className="row row-cols-2 row-cols-md-4 g-3 mt-3 mb-0 player-facts">
                    {[
                        ["Nacionalidad", selected.nationality],
                        ["Posición", selected.position],
                        ["Equipo", selected.currentTeam],
                        ["Liga", selected.league],
                    ].map(([label, value]) => (
                        <div className="col" key={label}>
                            <dt className="small text-body-secondary fw-normal mb-2">
                                {label}
                            </dt>
                            <dd className="small fw-semibold mb-0">
                                {value || "No disponible"}
                            </dd>
                        </div>
                    ))}
                </dl>
                <button
                    className="btn btn-link align-self-start px-0 mt-3"
                    onClick={onShowDetail}
                >
                    Ver detalle del jugador
                </button>
            </>
        )}
    </section>
);
