package com.cbo.players.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ValuationMetricsDto(
        String activeStrategyCode,
        String activeStrategyName,
        int activeStrategyVersion,
        LocalDateTime lastRecalculationAt,
        long coveredPlayers,
        BigDecimal averageQuote,
        BigDecimal minQuote,
        BigDecimal maxQuote
) {
}
