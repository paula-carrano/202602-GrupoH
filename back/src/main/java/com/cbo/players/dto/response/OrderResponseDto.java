package com.cbo.players.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponseDto(
        Long transactionId,
        String orderType,
        Long playerId,
        String playerName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        Integer tokensBefore,
        Integer tokensAfter,
        BigDecimal averageBuyPrice,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime timestamp
) {}
