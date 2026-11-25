package com.cbo.players.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MarketMetricsDto(
        long totalTransactions,
        Map<String, Long> transactionsByType,
        BigDecimal totalTradedCredits,
        long activeUsers,
        long totalIssuedTokens,
        long tokensInCirculation,
        double circulationRatioPercent,
        List<TopTradedPlayerDto> topTradedPlayers
) {
}
