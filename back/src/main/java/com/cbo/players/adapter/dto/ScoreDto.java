package com.cbo.players.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreDto(String winner, String duration, GoalsDto fullTime, GoalsDto halfTime) {}
