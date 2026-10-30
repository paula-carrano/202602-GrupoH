import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { PlayersPage } from "../pages/index";
import { api } from "../services/api";
import { saveSession } from "../services/session";

const renderPlayers = (session) =>
    render(
        <MemoryRouter>
            <PlayersPage session={session} onLogout={vi.fn()} />
        </MemoryRouter>,
    );

const makePlayers = () =>
    Array.from({ length: 12 }, (_, index) => ({
        id: index + 1,
        firstName: index === 11 ? "Julián" : index === 0 ? "Lionel" : "Jugador",
        lastName:
            index === 11 ? "Álvarez" : index === 0 ? "Messi" : `${index + 1}`,
        birthDate: null,
        nationality: "Argentina",
        position: "FORWARD",
        currentTeam: "Equipo",
        league: "PL",
    }));

describe("catálogo de jugadores", () => {
    beforeEach(() => {
        localStorage.clear();
        sessionStorage.clear();
    });
    afterEach(() => {
        vi.restoreAllMocks();
        localStorage.clear();
        sessionStorage.clear();
    });

    it("permite paginar y buscar en el catálogo", async () => {
        const user = userEvent.setup();
        vi.spyOn(api, "get").mockResolvedValue({ data: makePlayers() });
        const session = {
            token: "jwt-prueba",
            username: "jugador",
            expiresAt: Date.now() + 60_000,
        };
        saveSession(session, false);
        renderPlayers(session);

        expect(
            await screen.findByText("Mostrando 1–10 de 12 jugadores"),
        ).toBeInTheDocument();
        expect(screen.queryByText("Julián Álvarez")).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Siguiente" }));
        expect(screen.getByText("Julián Álvarez")).toBeInTheDocument();
        await user.type(
            screen.getByRole("textbox", { name: "Buscar jugador por nombre" }),
            "Messi",
        );
        expect(screen.getByText("Lionel Messi")).toBeInTheDocument();
        expect(
            screen.getByText("Mostrando 1–1 de 1 jugadores"),
        ).toBeInTheDocument();
    });

    it("muestra el error y permite reintentar después de cerrar el aviso", async () => {
        const user = userEvent.setup();
        const get = vi.spyOn(api, "get").mockRejectedValue({
            response: {
                status: 500,
                data: {
                    errorCode: "INTERNAL_SERVER_ERROR",
                    detail: "Technical details",
                },
            },
        });
        const session = {
            token: "jwt-prueba",
            username: "jugador",
            expiresAt: Date.now() + 60_000,
        };
        saveSession(session, false);
        renderPlayers(session);

        expect(await screen.findByRole("alertdialog")).toHaveTextContent(
            "Ocurrió un error en el servidor.",
        );
        expect(get).toHaveBeenCalledWith(
            "/players",
            expect.objectContaining({ signal: expect.any(AbortSignal) }),
        );
        await user.click(screen.getByRole("button", { name: "Cerrar aviso" }));
        await waitFor(() =>
            expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
        );
        expect(screen.getByRole("alert")).toHaveTextContent(
            "Ocurrió un error en el servidor.",
        );
        get.mockResolvedValueOnce({ data: makePlayers() });
        await user.click(screen.getByRole("button", { name: "Reintentar" }));
        expect(await screen.findByText("Lionel Messi")).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(get).toHaveBeenCalledTimes(2);
    });

    it("distingue la carga de una respuesta vacía", async () => {
        let resolve;
        vi.spyOn(api, "get").mockReturnValue(
            new Promise((done) => {
                resolve = done;
            }),
        );
        renderPlayers({ token: "jwt-prueba" });

        expect(screen.getByText("Cargando jugadores…")).toBeInTheDocument();
        expect(
            screen.queryByText("No se encontraron jugadores."),
        ).not.toBeInTheDocument();
        await act(async () => resolve({ data: [] }));
        expect(
            screen.getByText("No se encontraron jugadores."),
        ).toBeInTheDocument();
        expect(
            screen.queryByText("Cargando jugadores…"),
        ).not.toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("muestra vacío al buscar sin coincidencias y recupera el catálogo al limpiar", async () => {
        const user = userEvent.setup();
        vi.spyOn(api, "get").mockResolvedValue({ data: makePlayers() });
        renderPlayers({ token: "jwt-prueba" });
        await screen.findByText("Lionel Messi");
        const search = screen.getByRole("textbox", {
            name: "Buscar jugador por nombre",
        });
        await user.type(search, "sin coincidencias");
        expect(
            screen.getByText("No se encontraron jugadores."),
        ).toBeInTheDocument();
        await user.clear(search);
        expect(screen.getByText("Lionel Messi")).toBeInTheDocument();
    });
});
