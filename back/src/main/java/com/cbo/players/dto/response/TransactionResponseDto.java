package com.cbo.players.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponseDto(
        Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime timestamp,
        Long actorUserId,
        String operationType,
        Long playerId,
        String playerName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        Integer tokensBefore,
        Integer tokensAfter,
        Integer strategyVersion
) {}
