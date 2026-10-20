import { Outlet } from "react-router-dom";
import { DashboardLayout } from "./DashboardLayout";

export const MarketWorkspace = ({ session, onLogout }) => (
    <DashboardLayout session={session} onLogout={onLogout}>
        <Outlet />
    </DashboardLayout>
);
