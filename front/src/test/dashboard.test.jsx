import { afterEach, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { api } from "../services/api";
import { Home, DashboardPresentation } from "../pages/Home";

afterEach(() => vi.restoreAllMocks());
it("carga el catalogo al entrar sin consultas financieras", async () => {
    const get = vi.spyOn(api, "get").mockResolvedValue({
        data: [{ id: 7, firstName: "Ana", lastName: "Sur", league: "Liga A" }],
    });
    render(
        <MemoryRouter>
            <Home />
        </MemoryRouter>,
    );
    for (const name of [
        "Resumen financiero",
        "Evolución histórica de cotizaciones",
        "Distribución de la cartera",
        "Evolución del patrimonio y saldo",
    ])
        expect(screen.getByRole("heading", { name })).toBeInTheDocument();
    expect(
        screen.getByText(/Seleccioná un jugador para consultar/),
    ).toBeInTheDocument();
    expect(
        await screen.findByRole("button", { name: /Ana Sur/ }),
    ).toBeInTheDocument();
    expect(
        screen.getByRole("button", { name: "Liga A", exact: true }),
    ).toBeInTheDocument();
    expect(
        screen.queryByRole("button", {
            name: "Elegir jugador para cotizaciones",
        }),
    ).not.toBeInTheDocument();
    expect(get.mock.calls.map((call) => call[0])).toEqual(["/players"]);
});
it("un error de cotizaciones no oculta el resumen ni confunde cero con desconocido", async () => {
    const retry = vi.fn();
    render(
        <DashboardPresentation
            summary={{
                state: "partial",
                data: {
                    availableBalance: 0,
                    totalWealth: 85,
                    profitOrLoss: 15,
                },
            }}
            quotes={{ state: "error", onRetry: retry }}
            allocation={{ state: "empty" }}
            wealth={{ state: "loading" }}
        />,
    );
    expect(screen.getByText("0")).toBeInTheDocument();
    expect(screen.getByText("85")).toBeInTheDocument();
    expect(screen.queryByText("100")).not.toBeInTheDocument();
    expect(screen.getAllByText("No disponible").length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(retry).toHaveBeenCalledTimes(1);
});
it("selecciona jugador del catálogo real, abre perfil y restaura foco al cerrar con Escape", async () => {
    const user = userEvent.setup();
    const player = { id: 7, firstName: "Jugador", lastName: "Prueba" };
    const get = vi
        .spyOn(api, "get")
        .mockResolvedValueOnce({ data: [player] })
        .mockResolvedValueOnce({ data: player });
    render(
        <MemoryRouter>
            <Home />
        </MemoryRouter>,
    );
    await user.selectOptions(
        await screen.findByRole("combobox", { name: "Jugador" }),
        "7",
    );
    const trigger = screen.getByRole("button", {
        name: "Ver detalle del jugador",
    });
    await user.click(trigger);
    const dialog = screen.getByRole("dialog");
    expect(
        within(dialog).getByRole("button", { name: "Cerrar" }),
    ).toHaveFocus();
    await user.tab();
    expect(
        within(dialog).getByRole("button", { name: "Cerrar" }),
    ).toHaveFocus();
    await user.tab({ shift: true });
    expect(
        within(dialog).getByRole("button", { name: "Cerrar" }),
    ).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
    expect(get.mock.calls.map((call) => call[0])).toEqual([
        "/players",
        "/players/7",
    ]);
});
it("recupera un error de catálogo y distingue vacío confirmado", async () => {
    vi.spyOn(api, "get")
        .mockRejectedValueOnce({ response: { status: 403 } })
        .mockResolvedValueOnce({ data: [] });
    render(
        <MemoryRouter>
            <Home />
        </MemoryRouter>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
        "No tenés permiso",
    );
    await userEvent.click(
        screen.getByRole("button", { name: "Reintentar catálogo" }),
    );
    expect(
        await screen.findByText("No hay jugadores disponibles."),
    ).toBeInTheDocument();
});

it("filtra la lista lateral y selecciona la ficha sin perder la selección al filtrar", async () => {
    const user = userEvent.setup();
    vi.spyOn(api, "get").mockResolvedValue({
        data: [
            {
                id: 7,
                firstName: "Ana",
                lastName: "Sur",
                league: "Liga A",
                currentTeam: "Club Sur",
            },
            {
                id: 8,
                firstName: "Sol",
                lastName: "Norte",
                league: "Liga B",
                currentTeam: "Club Norte",
            },
        ],
    });
    render(
        <MemoryRouter>
            <Home />
        </MemoryRouter>,
    );
    await user.click(await screen.findByRole("button", { name: /Ana Sur/ }));
    expect(
        screen.getByRole("heading", { name: "Ana Sur" }),
    ).toBeInTheDocument();
    await user.click(
        screen.getByRole("button", { name: "Liga B", exact: true }),
    );
    expect(
        screen.queryByRole("button", { name: /Ana Sur/ }),
    ).not.toBeInTheDocument();
    expect(
        screen.getByRole("heading", { name: "Ana Sur" }),
    ).toBeInTheDocument();
    await user.type(
        screen.getByRole("searchbox", { name: "Buscar jugador" }),
        "inexistente",
    );
    expect(
        screen.getByText("No hay jugadores que coincidan con los filtros."),
    ).toBeInTheDocument();
    expect(
        screen.getByRole("button", { name: "Comprar tokens" }),
    ).toBeDisabled();
});
