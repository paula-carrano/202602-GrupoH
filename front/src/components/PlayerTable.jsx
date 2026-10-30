import { formatBirthDate } from "../utils/date";

export const PlayerTable = ({
    players,
    hasResults,
    rankingOffset = 0,
    onPlayerSelect,
}) => (
    <section
        className="table-responsive catalog-table-scroll"
        aria-label="Catálogo de jugadores"
        tabIndex={0}
    >
        <table className="table players-table mb-0">
            <caption className="visually-hidden">Catálogo de jugadores</caption>
            <thead>
                <tr>
                    <th scope="col" className="text-center">
                        N.º de fila
                    </th>
                    <th scope="col">Nombre completo</th>
                    <th scope="col" className="text-center">
                        Nacimiento
                    </th>
                    <th scope="col" className="text-center">
                        Nacionalidad
                    </th>
                    <th scope="col" className="text-center">
                        Posición
                    </th>
                    <th scope="col" className="text-center">
                        Club actual
                    </th>
                    <th scope="col" className="text-center">
                        Liga
                    </th>
                </tr>
            </thead>
            <tbody>
                {players.map((player, index) => (
                    <tr key={player.id}>
                        <td className="text-center">
                            {rankingOffset + index + 1}
                        </td>
                        <td>
                            <button
                                type="button"
                                className="player-name-button"
                                onClick={() => onPlayerSelect?.(player)}
                                aria-label={`Ver perfil de ${player.firstName} ${player.lastName}`}
                            >
                                {player.firstName} {player.lastName}
                            </button>
                        </td>
                        <td className="text-center">
                            {formatBirthDate(player.birthDate)}
                        </td>
                        <td className="text-center">
                            {player.nationality || "N/D"}
                        </td>
                        <td className="text-center">
                            {player.position || "N/D"}
                        </td>
                        <td className="text-center">{player.currentTeam}</td>
                        <td className="text-center">{player.league}</td>
                    </tr>
                ))}
            </tbody>
        </table>

        {!hasResults && (
            <output className="empty-results text-center p-4 mb-0 d-block">
                No se encontraron jugadores.
            </output>
        )}
    </section>
);
