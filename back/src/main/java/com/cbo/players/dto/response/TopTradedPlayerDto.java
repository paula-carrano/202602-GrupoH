package com.cbo.players.dto.response;

import java.math.BigDecimal;

public record TopTradedPlayerDto(
        Long playerId,
        String playerName,
        String team,
        long totalTokensTraded,
        BigDecimal totalVolumeCredits
) {
}
