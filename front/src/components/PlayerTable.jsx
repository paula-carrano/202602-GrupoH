export const PlayerTable = ({ players, hasResults }) => (
  <div className="table-responsive catalog-table-scroll">
    <table className="table players-table mb-0">
      <thead><tr><th scope="col">ID</th><th scope="col">Nombre completo</th><th scope="col">Nacimiento</th><th scope="col">Nacionalidad</th><th scope="col">Posición</th><th scope="col">Club actual</th><th scope="col">Liga</th></tr></thead>
      <tbody>{players.map(player => <tr key={player.id}><td>{player.id}</td><td>{player.firstName} {player.lastName}</td><td>{player.birthDate || 'N/D'}</td><td>{player.nationality}</td><td>{player.position}</td><td>{player.currentTeam}</td><td>{player.league}</td></tr>)}</tbody>
    </table>
    {!hasResults && <p className="empty-results text-center p-4 mb-0">No se encontraron jugadores.</p>}
  </div>
)
