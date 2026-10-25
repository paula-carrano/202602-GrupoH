package com.cbo.players.dto.response;

import java.math.BigDecimal;

public record RankingItemDto(
        Integer rank,
        Long playerId,
        String firstName,
        String lastName,
        String position,
        String team,
        String league,
        BigDecimal currentQuote,
        String currency
) {}
