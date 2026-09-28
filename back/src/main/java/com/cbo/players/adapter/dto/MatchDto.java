package com.cbo.players.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(Long id, String competition, Instant utcDate, String status,
        Integer matchday, TeamDto homeTeam, TeamDto awayTeam, ScoreDto score) {
    public MatchDto {
        Objects.requireNonNull(id);
        Objects.requireNonNull(competition);
        Objects.requireNonNull(utcDate);
        Objects.requireNonNull(status);
        Objects.requireNonNull(homeTeam);
        Objects.requireNonNull(awayTeam);
    }
}
