import { useMemo, useState } from "react";
import { useSessionRequest } from "./useSessionRequest";
import { getPlayers } from "../services/players";

const PAGE_SIZE = 10;

export const usePlayerCatalog = ({ session, onLogout }) => {
	const [dismissedError, setDismissedError] = useState(null);
	const [query, setQuery] = useState("");
	const [page, setPage] = useState(1);
	const resource = useSessionRequest({ session, requestKey: 'catalog', request: getPlayers, onUnauthorized: onLogout });
	const players = resource.data || [];

	const filtered = useMemo(
		() =>
			players.filter((player) =>
				`${player.firstName} ${player.lastName}`
					.toLocaleLowerCase()
					.includes(query.toLocaleLowerCase().trim()),
			),
		[players, query],
	);
	const visible = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

	return {
		error: resource.error,
		filteredCount: filtered.length,
		loading: resource.loading,
		modalError: dismissedError === resource.key ? '' : resource.error,
		page,
		pageSize: PAGE_SIZE,
		query,
		retry: resource.retry,
		setPage,
		setQuery: (value) => {
			setQuery(value);
			setPage(1);
		},
		closeModalError: () => setDismissedError(resource.key),
		visible,
	};
};
