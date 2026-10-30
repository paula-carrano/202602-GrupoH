import { useMarket } from "../hooks/useMarket";
import { requestState } from "../utils/financial";
import { QuoteExplorer } from "../components/QuoteExplorer";
import { WealthExplorer } from "../components/WealthExplorer";
import { useState } from "react";
import { Link } from "react-router-dom";
import { useSessionRequest } from "../hooks/useSessionRequest";
import { getPlayers } from "../services/players";
import { FinancialSummary } from "../components/FinancialSummary";
import { QuoteHistoryChart } from "../components/charts/QuoteHistoryChart";
import { PortfolioAllocationChart } from "../components/charts/PortfolioAllocationChart";
import { WealthHistoryChart } from "../components/charts/WealthHistoryChart";
import { PlayerProfileDialog } from "../components/PlayerProfileDialog";

export const DashboardPresentation = ({
    summary,
    quotes,
    allocation,
    wealth,
}) => (
    <div className="row g-3">
        <div className="col-12 order-4">
            <FinancialSummary {...summary} />
        </div>
        <div className="col-12 col-xxl-7">
            <QuoteHistoryChart {...quotes} />
        </div>
        <div className="col-12 col-xxl-5">
            <PortfolioAllocationChart {...allocation} />
        </div>
        <div className="col-12">
            <WealthHistoryChart {...wealth} />
        </div>
    </div>
);

const playerName = (player) =>
    `${player.firstName || ""} ${player.lastName || ""}`.trim();
const initials = (player) =>
    `${player.firstName?.[0] || ""}${player.lastName?.[0] || ""}` || "—";

export const Home = ({ session, onLogout }) => {
    const market = useMarket();
    const state = market ? requestState(market.portfolio) : "unavailable";
    const [loadPlayers, setLoadPlayers] = useState(false);
    const [selectedId, setSelectedId] = useState("");
    const [showDetail, setShowDetail] = useState(false);
    const [query, setQuery] = useState("");
    const [league, setLeague] = useState("");
    const catalog = useSessionRequest({
        session,
        requestKey: "dashboard-players",
        request: getPlayers,
        enabled: loadPlayers,
        onUnauthorized: onLogout,
    });
    const players = catalog.data || [];
    const selected = players.find((player) => String(player.id) === selectedId);
    const leagues = [
        ...new Set(players.map((player) => player.league).filter(Boolean)),
    ].sort();
    const filtered = players.filter(
        (player) =>
            (!league || player.league === league) &&
            playerName(player)
                .toLocaleLowerCase()
                .includes(query.trim().toLocaleLowerCase()),
    );
    const selectPlayer = (id) => {
        setSelectedId(String(id));
        setShowDetail(false);
    };
    const position = market?.view.positions.find(
        (item) => item.playerId === selected?.id,
    );
    return (
        <>
            <h1 className="visually-hidden">Inicio financiero</h1>
            <div className="row g-4 investment-workspace">
                <aside className="col-12 col-xl-4 col-xxl-3">
                    <section
                        className="investment-catalog card p-3"
                        aria-label="Selección de jugador"
                    >
                        <div className="d-flex align-items-center justify-content-between mb-3">
                            <h2 className="h6 mb-0">Jugadores</h2>
                            <span className="badge rounded-pill text-bg-dark">
                                {players.length}
                            </span>
                        </div>
                        <input
                            className="form-control mb-3"
                            type="search"
                            aria-label="Buscar jugador"
                            placeholder="Buscar jugador…"
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                        />
                        <div
                            className="d-flex flex-wrap gap-1 mb-3"
                            aria-label="Filtrar por liga"
                        >
                            {["", ...leagues].map((item) => (
                                <button
                                    key={item}
                                    className={`btn btn-sm ${league === item ? "btn-primary" : "btn-outline-secondary"}`}
                                    aria-pressed={league === item}
                                    onClick={() => setLeague(item)}
                                >
                                    {item || "Todas"}
                                </button>
                            ))}
                        </div>
                        {!loadPlayers && (
                            <button
                                className="btn btn-primary mb-3"
                                onClick={() => setLoadPlayers(true)}
                            >
                                Elegir jugador para cotizaciones
                            </button>
                        )}
                        {catalog.loading && (
                            <p role="status">Cargando jugadores…</p>
                        )}
                        {catalog.error && (
                            <div role="alert">
                                {catalog.error}{" "}
                                <button
                                    className="btn btn-outline-primary"
                                    onClick={catalog.retry}
                                >
                                    Reintentar catálogo
                                </button>
                            </div>
                        )}
                        {loadPlayers &&
                            !catalog.loading &&
                            !catalog.error &&
                            players.length === 0 && (
                                <p role="status">
                                    No hay jugadores disponibles.
                                </p>
                            )}
                        {players.length > 0 && (
                            <>
                                <label className="d-xl-none mb-3">
                                    Jugador
                                    <select
                                        className="form-select"
                                        value={selectedId}
                                        onChange={(event) =>
                                            selectPlayer(event.target.value)
                                        }
                                    >
                                        <option value="">
                                            Seleccioná un jugador
                                        </option>
                                        {players.map((player) => (
                                            <option
                                                key={player.id}
                                                value={player.id}
                                            >
                                                {playerName(player)}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <div className="investment-player-list list-group list-group-flush overflow-y-auto">
                                    {filtered.map((player) => (
                                        <button
                                            key={player.id}
                                            type="button"
                                            aria-pressed={
                                                String(player.id) === selectedId
                                            }
                                            className={`list-group-item list-group-item-action d-flex align-items-center gap-3 px-2 py-3 ${String(player.id) === selectedId ? "active" : ""}`}
                                            onClick={() =>
                                                selectPlayer(player.id)
                                            }
                                        >
                                            <span
                                                className="player-avatar rounded-circle d-inline-flex align-items-center justify-content-center flex-shrink-0"
                                                aria-hidden="true"
                                            >
                                                {initials(player)}
                                            </span>
                                            <span className="text-start">
                                                <strong className="d-block">
                                                    {playerName(player)}
                                                </strong>
                                                <small className="d-block text-body-secondary">
                                                    {player.currentTeam ||
                                                        "Equipo no disponible"}{" "}
                                                    · {player.position || "—"}
                                                </small>
                                                <small className="d-block text-body-secondary mt-1">
                                                    {player.league ||
                                                        "Liga no disponible"}
                                                </small>
                                            </span>
                                        </button>
                                    ))}
                                </div>
                                {filtered.length === 0 && (
                                    <p role="status">
                                        No hay jugadores que coincidan con los
                                        filtros.
                                    </p>
                                )}
                            </>
                        )}
                        <Link className="btn btn-link mt-3" to="/players">
                            Explorar jugadores
                        </Link>
                    </section>
                </aside>
                <div className="col-12 col-xl-8 col-xxl-9">
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
                                    disabled={!selected || !market?.allowed}
                                    onClick={() =>
                                        market.openOrder("buy", selected.id)
                                    }
                                >
                                    Comprar tokens
                                </button>
                                <button
                                    className="btn btn-outline-danger"
                                    disabled={
                                        !selected ||
                                        !market?.allowed ||
                                        state !== "ready" ||
                                        !(position?.tokenQuantity > 0)
                                    }
                                    onClick={() =>
                                        market.openOrder("sell", selected.id)
                                    }
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
                                    onClick={() => setShowDetail(true)}
                                >
                                    Ver detalle del jugador
                                </button>
                            </>
                        )}
                    </section>
                    <nav
                        className="nav investment-section-nav border-bottom mb-3 gap-2"
                        aria-label="Secciones de inversión"
                    >
                        <a className="nav-link" href="#investment-summary">
                            Resumen
                        </a>
                        <a className="nav-link" href="#investment-quotes">
                            Cotización
                        </a>
                        <Link className="nav-link" to="/portfolio">
                            Mi portfolio
                        </Link>
                        <Link className="nav-link" to="/transactions">
                            Historial de operaciones
                        </Link>
                    </nav>
                    {!market?.allowed && (
                        <p className="small text-body-secondary" role="status">
                            El resumen financiero todavía no está disponible.
                        </p>
                    )}
                    <div className="row g-3">
                        <div
                            className="col-12 col-xxl-7"
                            id="investment-quotes"
                        >
                            {market && selected ? (
                                <QuoteExplorer
                                    key={selected.id}
                                    playerId={selected.id}
                                    playerLabel={playerName(selected)}
                                    session={session}
                                    onUnauthorized={onLogout}
                                    revision={market.revision}
                                />
                            ) : (
                                <QuoteHistoryChart
                                    playerLabel={
                                        selected
                                            ? playerName(selected)
                                            : undefined
                                    }
                                />
                            )}
                        </div>
                        <div className="col-12 col-xxl-5">
                            <PortfolioAllocationChart
                                state={state}
                                positions={market?.view.allocation}
                                error={market?.portfolio.error}
                                onRetry={market?.portfolio.retry}
                            />
                        </div>
                        <div className="col-12">
                            {market ? (
                                <WealthExplorer />
                            ) : (
                                <WealthHistoryChart />
                            )}
                        </div>
                        <div className="col-12" id="investment-summary">
                            <FinancialSummary
                                state={state}
                                data={market?.view.summary}
                                error={market?.portfolio.error}
                                onRetry={market?.portfolio.retry}
                            />
                        </div>
                    </div>
                </div>
            </div>
            <PlayerProfileDialog
                player={showDetail ? selected : null}
                session={session}
                onUnauthorized={onLogout}
                onClose={() => setShowDetail(false)}
            />
        </>
    );
};
