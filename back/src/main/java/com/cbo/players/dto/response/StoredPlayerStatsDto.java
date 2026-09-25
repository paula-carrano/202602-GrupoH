package com.cbo.players.dto.response;
import com.cbo.players.adapter.dto.PlayerStatsDto;
import com.cbo.players.model.PlayerStatistics;
import java.time.Instant;
public record StoredPlayerStatsDto(Long playerId, Long whoscoredId, Instant lastSuccessfulSyncAt, PlayerStatsDto metrics) {
    public static StoredPlayerStatsDto from(PlayerStatistics stats) {
        return new StoredPlayerStatsDto(stats.getPlayerId(), stats.getWhoscoredId(), stats.getLastSuccessfulSyncAt(), stats.metrics());
    }
}
