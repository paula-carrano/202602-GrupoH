import { formatBirthDate } from '../utils/date'

export const PlayerTable = ({ players, hasResults, rankingOffset = 0 }) => (
    <div className="table-responsive catalog-table-scroll">
        <table className="table players-table mb-0">
            <thead>
                <tr>
                    <th scope="col" className="text-center">Ranking</th>
                    <th scope="col">Nombre completo</th>
                    <th scope="col" className="text-center">Nacimiento</th>
                    <th scope="col" className="text-center">Nacionalidad</th>
                    <th scope="col" className="text-center">Posición</th>
                    <th scope="col" className="text-center">Club actual</th>
                    <th scope="col" className="text-center">Liga</th>
                </tr>
            </thead>
            <tbody>
                {players.map((player, index) => (
                    <tr key={player.id}>
                        <td className="text-center">{rankingOffset + index + 1}</td>
                        <td>
                            {player.firstName} {player.lastName}
                        </td>
                        <td className="text-center">{formatBirthDate(player.birthDate)}</td>
                        <td className="text-center">{player.nationality}</td>
                        <td className="text-center">{player.position}</td>
                        <td className="text-center">{player.currentTeam}</td>
                        <td className="text-center">{player.league}</td>
                    </tr>
                ))}
            </tbody>
        </table>
        {!hasResults && (
            <p className="empty-results text-center p-4 mb-0">
                No se encontraron jugadores.
            </p>
        )}
    </div>
);
