package com.cbo.players.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamDto(Long id, String name, String shortName, String tla) {
    public TeamDto { Objects.requireNonNull(name); }
}
