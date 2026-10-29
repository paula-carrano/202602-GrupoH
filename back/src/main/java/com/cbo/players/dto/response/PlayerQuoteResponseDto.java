package com.cbo.players.dto.response;

import com.cbo.players.model.PlayerQuote;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlayerQuoteResponseDto(
        Long quoteId,
        Long playerId,
        BigDecimal price,
        String currency,
        String strategyName,
        Integer strategyVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime timestamp
) {
    public static PlayerQuoteResponseDto from(PlayerQuote quote) {
        return new PlayerQuoteResponseDto(
                quote.getId(),
                quote.getPlayer() != null ? quote.getPlayer().getId() : null,
                quote.getPrice(),
                quote.getCurrency(),
                quote.getStrategyName(),
                quote.getStrategyVersion(),
                quote.getTimestamp()
        );
    }
}
