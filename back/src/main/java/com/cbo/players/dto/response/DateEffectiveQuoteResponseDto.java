package com.cbo.players.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DateEffectiveQuoteResponseDto(
        Long quoteId,
        Long playerId,
        String effectiveDate,
        BigDecimal price,
        String currency,
        String strategyName,
        Integer strategyVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime quoteTimestamp
) {}
