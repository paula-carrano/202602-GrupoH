package com.cbo.players.dto.response;

import com.cbo.players.model.AlertCondition;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceAlertEventDto(
        Long alertId,
        Long playerId,
        String playerName,
        AlertCondition condition,
        BigDecimal targetPrice,
        BigDecimal currentPrice,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime triggeredAt
) {}
