package com.cbo.players.dto.response;

import java.util.List;

public record PlayerRankingResponseDto(
        String strategyCode,
        Integer strategyVersion,
        boolean cached,
        List<RankingItemDto> ranking
) {
    public PlayerRankingResponseDto withCached(boolean isCached) {
        return new PlayerRankingResponseDto(strategyCode, strategyVersion, isCached, ranking);
    }
}
