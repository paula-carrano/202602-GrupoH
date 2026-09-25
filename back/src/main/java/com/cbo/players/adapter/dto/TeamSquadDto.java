package com.cbo.players.adapter.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamSquadDto(CatalogTeamDto team, List<SquadPlayerDto> players) {
    public TeamSquadDto {
        Objects.requireNonNull(team);
        players = List.copyOf(players);
    }
}
