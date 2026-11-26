package com.cbo.players.dto.response;

public record PerformanceMetricsDto(
        long rankingCacheHits,
        long rankingCacheMisses,
        double rankingCacheHitRatioPercent,
        double avgRankingLatencyMs
) {
}
