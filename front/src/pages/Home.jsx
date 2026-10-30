import {
  PlayerCatalog,
  SelectedPlayerCard,
  QuoteExplorer,
  WealthExplorer,
  FinancialSummary,
  QuoteHistoryChart,
  PortfolioAllocationChart,
  WealthHistoryChart,
  PlayerProfileDialog,
} from "../components"
import { playerName } from "../utils/playerDisplay";
import { useMarket } from "../hooks/useMarket";
import { requestState } from "../utils/financial";

import { useState } from "react";
import { Link } from "react-router-dom";
import { useSessionRequest } from "../hooks/useSessionRequest";
import { getPlayers } from "../services/players";

export const DashboardPresentation = ({
    summary,
    quotes,
    allocation,
    wealth,
}) => (
    <div className="row g-3">
        <div className="col-12 order-4">
            <FinancialSummary {...summary} />
        </div>
        <div className="col-12 col-xxl-7">
            <QuoteHistoryChart {...quotes} />
        </div>
        <div className="col-12 col-xxl-5">
            <PortfolioAllocationChart {...allocation} />
        </div>
        <div className="col-12">
            <WealthHistoryChart {...wealth} />
        </div>
    </div>
);

export const Home = ({ session, onLogout }) => {
    const market = useMarket();
    const state = market ? requestState(market.portfolio) : "unavailable";
    const [selectedId, setSelectedId] = useState("");
    const [showDetail, setShowDetail] = useState(false);
    const catalog = useSessionRequest({
        session,
        requestKey: "dashboard-players",
        request: getPlayers,
        onUnauthorized: onLogout,
    });
    const players = catalog.data || [];
    const selected = players.find((player) => String(player.id) === selectedId);
    const selectPlayer = (id) => {
        setSelectedId(String(id));
        setShowDetail(false);
    };
    const position = market?.view.positions.find(
        (item) => item.playerId === selected?.id,
    );
    return (
        <>
            <h1 className="visually-hidden">Inicio financiero</h1>
            <div className="row g-4 investment-workspace">
                <aside className="col-12 col-xl-4 col-xxl-3">
                    <PlayerCatalog
                        players={players}
                        catalog={catalog}
                        selectedId={selectedId}
                        onSelect={selectPlayer}
                    />
                </aside>
                <div className="col-12 col-xl-8 col-xxl-9">
                    <SelectedPlayerCard
                        selected={selected}
                        canBuy={Boolean(selected && market?.allowed)}
                        canSell={Boolean(
                            selected &&
                            market?.allowed &&
                            state === "ready" &&
                            position?.tokenQuantity > 0,
                        )}
                        onBuy={() => market.openOrder("buy", selected.id)}
                        onSell={() => market.openOrder("sell", selected.id)}
                        onShowDetail={() => setShowDetail(true)}
                    />
                    <nav
                        className="nav investment-section-nav border-bottom mb-3 gap-2"
                        aria-label="Secciones de inversión"
                    >
                        <a className="nav-link" href="#investment-summary">
                            Resumen
                        </a>
                        <a className="nav-link" href="#investment-quotes">
                            Cotización
                        </a>
                        <Link className="nav-link" to="/portfolio">
                            Mi portfolio
                        </Link>
                        <Link className="nav-link" to="/transactions">
                            Historial de operaciones
                        </Link>
                    </nav>
                    {!market?.allowed && (
                        <output className="d-block mb-3 small text-body-secondary">
                            El resumen financiero todavía no está disponible.
                        </output>
                    )}
                    <div className="row g-3">
                        <div
                            className="col-12 col-xxl-7"
                            id="investment-quotes"
                        >
                            {market && selected ? (
                                <QuoteExplorer
                                    key={selected.id}
                                    playerId={selected.id}
                                    playerLabel={playerName(selected)}
                                    session={session}
                                    onUnauthorized={onLogout}
                                    revision={market.revision}
                                />
                            ) : (
                                <QuoteHistoryChart
                                    playerLabel={
                                        selected
                                            ? playerName(selected)
                                            : undefined
                                    }
                                />
                            )}
                        </div>
                        <div className="col-12 col-xxl-5">
                            <PortfolioAllocationChart
                                state={state}
                                positions={market?.view.allocation}
                                error={market?.portfolio.error}
                                onRetry={market?.portfolio.retry}
                            />
                        </div>
                        <div className="col-12">
                            {market ? (
                                <WealthExplorer />
                            ) : (
                                <WealthHistoryChart />
                            )}
                        </div>
                        <div className="col-12" id="investment-summary">
                            <FinancialSummary
                                state={state}
                                data={market?.view.summary}
                                error={market?.portfolio.error}
                                onRetry={market?.portfolio.retry}
                            />
                        </div>
                    </div>
                </div>
            </div>
            <PlayerProfileDialog
                player={showDetail ? selected : null}
                session={session}
                onUnauthorized={onLogout}
                onClose={() => setShowDetail(false)}
            />
        </>
    );
};
