import { useState } from "react";
import { Link } from "react-router-dom";
import { playerName, initials } from "../utils/playerDisplay";

export const PlayerCatalog = ({ players, catalog, selectedId, onSelect }) => {
    const [query, setQuery] = useState("");
    const [league, setLeague] = useState("");
    const leagues = [
        ...new Set(players.map((player) => player.league).filter(Boolean)),
    ].sort((a, b) => a.localeCompare(b, "es"));
    const filtered = players.filter(
        (player) =>
            (!league || player.league === league) &&
            playerName(player)
                .toLocaleLowerCase()
                .includes(query.trim().toLocaleLowerCase()),
    );
    return (
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
            {catalog.loading && (
                <output className="d-block mb-3">Cargando jugadores…</output>
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
            {!catalog.loading &&
                !catalog.error &&
                players.length === 0 && (
                    <output className="d-block mb-3">
                        No hay jugadores disponibles.
                    </output>
                )}
            {players.length > 0 && (
                <>
                    <label className="d-xl-none mb-3">
                        Jugador{" "}
                        <select
                            className="form-select"
                            value={selectedId}
                            onChange={(event) =>
                                onSelect(event.target.value)
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
                                    onSelect(player.id)
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
                        <output className="d-block mb-3">
                            No hay jugadores que coincidan con los
                            filtros.
                        </output>
                    )}
                </>
            )}
            <Link className="btn btn-link mt-3" to="/players">
                Explorar jugadores
            </Link>
        </section>
    );
};
