package com.cbo.players.dto.response;

import com.cbo.players.model.AlertCondition;
import com.cbo.players.model.AlertStatus;
import com.cbo.players.model.PriceAlert;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceAlertResponseDto(
        Long id,
        Long userId,
        Long playerId,
        String playerName,
        AlertCondition condition,
        BigDecimal targetPrice,
        AlertStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime triggeredAt
) {
    public static PriceAlertResponseDto from(PriceAlert alert) {
        String playerName = alert.getPlayer() != null
                ? alert.getPlayer().getFirstName() + " " + alert.getPlayer().getLastName()
                : null;
        return new PriceAlertResponseDto(
                alert.getId(),
                alert.getUser() != null ? alert.getUser().getId() : null,
                alert.getPlayer() != null ? alert.getPlayer().getId() : null,
                playerName,
                alert.getCondition(),
                alert.getTargetPrice(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getTriggeredAt()
        );
    }
}
