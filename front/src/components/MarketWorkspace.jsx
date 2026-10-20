import { Outlet } from "react-router-dom";
import { DashboardLayout } from "./DashboardLayout";
import { hasSessionIdentity } from "../services/session";

export const MarketWorkspace = ({ session, onLogout }) => (
    <DashboardLayout session={session} onLogout={onLogout}>
        <Outlet context={{ session, canQueryPersonalData: hasSessionIdentity(session) }} />
    </DashboardLayout>
);
