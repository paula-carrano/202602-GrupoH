import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { MarketWorkspace } from "./components/MarketWorkspace";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { Home } from "./pages/Home";
import { PlayersPage } from "./pages/PlayersPage";
import { ErrorPage } from "./pages/ErrorPage";
import { clearSession, getSession, subscribeSession } from "./services/session";

const UnavailablePage = ({ title }) => (
    <>
        <h1 className="fs-5 fw-bold mb-3">{title}</h1>
        <p role="status">Esta sección todavía no está disponible.</p>
    </>
);

export const App = () => {
    const [session, setSession] = useState(getSession);
    const [sessionGeneration, setSessionGeneration] = useState(0);

    useEffect(() => subscribeSession(() => {
        setSession(getSession());
        setSessionGeneration(value => value + 1);
    }), []);
    useEffect(() => {
        if (!session) return;
        const timer = setTimeout(() => {
            if (session.expiresAt <= Date.now() && (!getSession() || getSession()?.token === session.token)) clearSession();
            setSession(getSession());
        }, Math.max(0, Math.min(session.expiresAt - Date.now(), 2147483647)));
        return () => clearTimeout(timer);
    }, [session]);

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
                    ? <MarketWorkspace key={`${sessionGeneration}:${session.token}`} session={session} onLogout={signOut} />
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
