package com.cbo.players.dto.response;

import com.cbo.players.model.PlayerStatistics;

import java.time.Instant;

public record PlayerStatisticsResponseDto(
        Integer assists,
        Integer dribbles,
        Integer goals,
        Integer keyPasses,
        Instant lastSuccessfulSyncAt,
        Integer minutesPlayed,
        Double rating,
        Integer shots,
        Integer tackles,
        Integer yellowCards,
        Integer redCards
) {
    public static PlayerStatisticsResponseDto fromEntity(PlayerStatistics statistics) {
        var metrics = statistics.metrics();
        return new PlayerStatisticsResponseDto(
                metrics.assists(),
                metrics.dribbles(),
                metrics.goals(),
                metrics.keyPasses(),
                statistics.getLastSuccessfulSyncAt(),
                metrics.minutosJugados(),
                metrics.rating(),
                metrics.shots(),
                metrics.tackles(),
                metrics.tarjetasAmarillas(),
                metrics.tarjetasRojas()
        );
    }
}
