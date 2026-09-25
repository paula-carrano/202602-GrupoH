package com.cbo.players.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerStatsDto(Integer goals, Integer assists, Integer shots, Integer keyPasses,
        Integer dribbles, Integer tackles, Double rating, Integer minutosJugados,
        Integer tarjetasAmarillas, Integer tarjetasRojas) {
    public PlayerStatsDto {
        Objects.requireNonNull(goals);
        Objects.requireNonNull(assists);
        Objects.requireNonNull(shots);
        Objects.requireNonNull(keyPasses);
        Objects.requireNonNull(dribbles);
        Objects.requireNonNull(tackles);
        Objects.requireNonNull(rating);
        Objects.requireNonNull(minutosJugados);
        Objects.requireNonNull(tarjetasAmarillas);
        Objects.requireNonNull(tarjetasRojas);
    }
}
