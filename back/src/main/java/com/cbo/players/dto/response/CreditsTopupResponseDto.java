package com.cbo.players.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditsTopupResponseDto(
        Long transactionId,
        Long userId,
        String operationType,
        BigDecimal amountCredited,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime timestamp
) {}
