package com.cbo.players.adapter.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
@JsonIgnoreProperties(ignoreUnknown = true)
public record SquadPlayerDto(Long id, String name, String firstName, String lastName,
        LocalDate birthDate, String nationality, String position) {
    public SquadPlayerDto {
        if (id == null || id <= 0 || name == null || name.isBlank()) throw new IllegalArgumentException("Invalid player");
    }
}
