import { useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { MarketWorkspace } from "./components/MarketWorkspace";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { Home } from "./pages/Home";
import { PlayersPage } from "./pages/PlayersPage";
import { ErrorPage } from "./pages/ErrorPage";
import { clearSession, getSession } from "./services/session";

const UnavailablePage = ({ title }) => (
    <>
        <h1 className="fs-5 fw-bold mb-3">{title}</h1>
        <p role="status">Esta sección todavía no está disponible.</p>
    </>
);

export const App = () => {
    const [session, setSession] = useState(getSession);

    const signOut = () => {
        clearSession();
        setSession(null);
    };

    return (
        <Routes>
            <Route path="/" element={<Navigate to="/home" replace />} />
            <Route
                path="/login"
                element={session
                    ? <Navigate to="/home" replace />
                    : <LoginPage onLogin={setSession} />}
            />
            <Route
                path="/register"
                element={session ? <Navigate to="/home" replace /> : <RegisterPage />}
            />
            <Route
                element={session
                    ? <MarketWorkspace session={session} onLogout={signOut} />
                    : <Navigate to="/login" replace />}
            >
                <Route path="/home" element={<Home />} />
                <Route path="/players" element={<PlayersPage session={session} onLogout={signOut} />} />
                <Route path="/portfolio" element={<UnavailablePage title="Mi portfolio" />} />
                <Route path="/transactions" element={<UnavailablePage title="Historial" />} />
                <Route path="/ranking" element={<UnavailablePage title="Ranking" />} />
                <Route path="/alerts" element={<UnavailablePage title="Alertas" />} />
            </Route>
            <Route path="/error" element={<ErrorPage />} />
            <Route path="*" element={<ErrorPage notFound />} />
        </Routes>
    );
};
