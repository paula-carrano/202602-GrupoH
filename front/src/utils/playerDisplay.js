export const playerName = (player) =>
    `${player.firstName || ""} ${player.lastName || ""}`.trim();
export const initials = (player) =>
    `${player.firstName?.[0] || ""}${player.lastName?.[0] || ""}` || "—";

