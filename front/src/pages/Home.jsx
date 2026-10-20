import { Link } from "react-router-dom";

export const Home = () => (
    <>
        <h1 className="fs-5 fw-bold mb-3">Inicio financiero</h1>
        <p role="status">El resumen financiero todavía no está disponible.</p>
        <div>
            <Link className="btn btn-primary" to="/players">Explorar jugadores</Link>
        </div>
    </>
);
