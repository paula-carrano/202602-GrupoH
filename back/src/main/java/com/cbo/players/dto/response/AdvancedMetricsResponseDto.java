package com.cbo.players.dto.response;

import java.time.Instant;

public record AdvancedMetricsResponseDto(
        MarketMetricsDto market,
        ValuationMetricsDto valuation,
        AlertMetricsDto alerts,
        PerformanceMetricsDto performance,
        Instant generatedAt
) {
}
