import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { Login, Register } from "./pages/AuthPages";
import { Home } from "./pages/Home";
import { ErrorPage } from "./pages/ErrorPage";
import { clearSession, getSession } from "./services/session";
import "bootstrap/dist/css/bootstrap.min.css";
import "./style.css";

const App = () => {
    const [session, setSession] = useState(getSession);

    const signOut = () => {
        clearSession();
        setSession(null);
    }

    return (
        <Routes>
            <Route path="/" element={<Navigate to="/home" replace />} />
            <Route
                path="/login"
                element={
                    session ? (
                        <Navigate to="/home" replace />
                    ) : (
                        <Login onLogin={setSession} />
                    )
                }
            />
            <Route
                path="/register"
                element={
                    session ? <Navigate to="/home" replace /> : <Register />
                }
            />
            <Route
                path="/home"
                element={<Home session={session} onLogout={signOut} preview={!session} />}
            />
            <Route path="/error" element={<ErrorPage />} />
            <Route path="*" element={<ErrorPage notFound />} />
        </Routes>
    );
}

createRoot(document.getElementById("root")).render(
    <React.StrictMode>
        <BrowserRouter>
            <App />
        </BrowserRouter>
    </React.StrictMode>,
);
