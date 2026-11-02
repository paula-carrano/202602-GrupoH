import { MarketProvider } from './MarketProvider';
import { Outlet } from "react-router-dom";
import { DashboardLayout } from "./DashboardLayout";
import { hasSessionIdentity } from "../services/session";

export const MarketWorkspace = ({ session, onLogout }) => (
    <DashboardLayout session={session} onLogout={onLogout}>
        <MarketProvider session={session} onLogout={onLogout}><Outlet context={{ session, canQueryPersonalData: hasSessionIdentity(session) }} /></MarketProvider>
    </DashboardLayout>
);
