import { Link, NavLink } from "react-router-dom";
import {
    FaFutbol,
    FaHouse,
    FaRightFromBracket,
    FaUser,
    FaWallet,
    FaClockRotateLeft,
    FaRankingStar,
    FaBell,
} from "react-icons/fa6";

const navigation = [
    { to: "/home", label: "Inicio", icon: FaHouse },
    { to: "/players", label: "Jugadores", icon: FaFutbol },
    { to: "/portfolio", label: "Portfolio", icon: FaWallet },
    { to: "/transactions", label: "Historial", icon: FaClockRotateLeft },
    { to: "/ranking", label: "Ranking", icon: FaRankingStar },
    { to: "/alerts", label: "Alertas", icon: FaBell },
];

export const DashboardLayout = ({ session, preview, onLogout, children }) => (
    <main className="dashboard-page container-fluid p-0 min-vh-100">
        <div className="dashboard d-flex flex-column flex-lg-row min-vh-100">
            <aside className="dashboard-sidebar d-flex flex-column flex-shrink-0 sticky-lg-top align-self-lg-start">
                <Link
                    className="dashboard-brand d-inline-flex align-items-center gap-3 p-4 flex-shrink-0 text-white text-decoration-none"
                    to="/home"
                >
                    <FaFutbol aria-hidden="true" />
                    <div className="dashboard-brand-copy d-flex flex-column gap-1"><strong>FootMarket</strong><span>Mercado de Jugadores</span></div>
                </Link>
                <nav
                    className="d-flex flex-lg-column gap-1 p-2 overflow-x-auto"
                    aria-label="Navegación principal"
                >
                    {navigation.map(({ to, label, icon: Icon }) => (
                        <NavLink
                            key={to}
                            to={to}
                            end
                            className={({ isActive }) =>
                                `d-inline-flex align-items-center gap-3 text-white text-decoration-none rounded px-3 py-3${isActive ? " active" : ""}`
                            }
                        >
                            <Icon aria-hidden="true" /> {label}
                        </NavLink>
                    ))}
                </nav>
                <div className="sidebar-footer d-none d-lg-flex align-items-center gap-3 mt-auto p-3"><FaFutbol aria-hidden="true" /><span>Tu pasión por el fútbol.<br />Tu próximo movimiento.</span></div>
            </aside>
            <div className="dashboard-content d-flex flex-column flex-grow-1">
                <header className="dashboard-header d-flex align-items-center justify-content-between flex-wrap gap-3 px-3 py-2">
                    <span className="dashboard-header-title">Dashboard</span>
                    <div className="dashboard-account d-inline-flex align-items-center gap-2">
                        <FaUser aria-hidden="true" />
                        <span>{preview ? "Vista previa" : session.username}</span>
                        {preview ? (
                            <Link className="text-white ms-2" to="/login">
                                Iniciar sesión
                            </Link>
                        ) : (
                            <button
                                className="btn btn-link text-white text-decoration-none d-inline-flex align-items-center gap-2 p-0 ms-2"
                                type="button"
                                onClick={onLogout}
                            >
                                <FaRightFromBracket aria-hidden="true" /> Salir
                            </button>
                        )}
                    </div>
                </header>
                <section className="catalog-panel d-flex flex-column flex-grow-1 p-3 p-md-4">
                    {children}
                </section>
            </div>
        </div>
    </main>
);
