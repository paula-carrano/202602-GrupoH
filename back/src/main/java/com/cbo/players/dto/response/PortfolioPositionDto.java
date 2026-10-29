package com.cbo.players.dto.response;

import java.math.BigDecimal;

public record PortfolioPositionDto(
        Long playerId,
        String playerName,
        String position,
        String team,
        Integer tokensQuantity,
        BigDecimal averageBuyPrice,
        BigDecimal currentQuote,
        BigDecimal totalCurrentValue,
        BigDecimal totalCostBasis,
        BigDecimal pnlNominal,
        BigDecimal pnlPercentage
) {}
