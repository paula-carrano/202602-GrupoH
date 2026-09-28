package com.cbo.players.adapter.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogTeamDto(Long id, String name) {
    public CatalogTeamDto {
        if (id == null || id <= 0 || name == null || name.isBlank()) throw new IllegalArgumentException("Invalid team");
    }
}
