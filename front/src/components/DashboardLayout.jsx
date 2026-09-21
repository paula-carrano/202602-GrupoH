import { Link } from 'react-router-dom'
import { FaFileLines, FaFutbol, FaHouse, FaRightFromBracket, FaUser } from 'react-icons/fa6'

export const DashboardLayout = ({ session, preview, onLogout, children }) => (
  <main className="dashboard-page container-fluid p-2 p-md-4 min-vh-100">
    <div className="dashboard d-flex flex-column mx-auto overflow-hidden rounded shadow-sm">
      <header className="dashboard-header d-flex align-items-center justify-content-between flex-wrap gap-3 px-3 py-2">
        <Link className="dashboard-brand d-inline-flex align-items-center gap-2 text-white text-decoration-none" to="/home">
          <FaFutbol aria-hidden="true" /><strong>FOOTBALL MARKET PLATFORM</strong><span className="ms-2">[v1.0 · Foundation]</span>
        </Link>
        <div className="dashboard-account d-inline-flex align-items-center gap-2">
          <FaUser aria-hidden="true" /><span>{preview ? 'Vista previa' : session.username}</span>
          {preview ? <Link className="text-white ms-2" to="/login">Iniciar sesión</Link> : <button className="btn btn-link text-white text-decoration-none d-inline-flex align-items-center gap-2 p-0 ms-2" type="button" onClick={onLogout}><FaRightFromBracket aria-hidden="true" /> Salir</button>}
        </div>
      </header>
      <div className="row g-0 flex-grow-1">
        <nav className="dashboard-sidebar col-lg-2 d-flex flex-lg-column gap-1 p-2" aria-label="Navegación principal">
          <Link className="d-inline-flex align-items-center gap-2 text-white text-decoration-none rounded px-3 py-2" to="/home"><FaHouse aria-hidden="true" /> Inicio</Link>
          <Link className="active d-inline-flex align-items-center gap-2 text-white text-decoration-none rounded px-3 py-2" to="/home" aria-current="page"><FaFutbol aria-hidden="true" /> Jugadores</Link>
          <a className="d-inline-flex align-items-center gap-2 text-white text-decoration-none rounded px-3 py-2" href="http://localhost:8080/swagger-ui/index.html" target="_blank" rel="noreferrer"><FaFileLines aria-hidden="true" /> Swagger Docs</a>
        </nav>
        <section className="catalog-panel col-lg-10 d-flex flex-column p-3 p-md-4">{children}</section>
      </div>
    </div>
  </main>
)
