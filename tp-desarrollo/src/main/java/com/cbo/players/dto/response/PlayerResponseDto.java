package com.cbo.players.dto.response;

import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;

import java.time.LocalDate;

public record PlayerResponseDto(
    Long id,
    String firstName,
    String lastName,
    LocalDate birthDate,
    String nationality,
    PlayerPosition position,
    String currentTeam,
    String league,
    boolean active
) {
    public static PlayerResponseDto fromEntity(Player player) {
        return new PlayerResponseDto(
            player.getId(),
            player.getFirstName(),
            player.getLastName(),
            player.getBirthDate(),
            player.getNationality(),
            player.getPosition(),
            player.getCurrentTeam(),
            player.getLeague(),
            player.isActive()
        );
    }
}
