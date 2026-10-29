package com.cbo.players.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponseDto(
        Long userId,
        BigDecimal creditsBalance,
        BigDecimal totalInvestedCost,
        BigDecimal totalCurrentValue,
        BigDecimal totalPnLNominal,
        BigDecimal totalPnLPercentage,
        List<PortfolioPositionDto> positions
) {}
