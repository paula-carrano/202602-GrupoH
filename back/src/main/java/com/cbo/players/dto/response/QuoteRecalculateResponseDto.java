package com.cbo.players.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record QuoteRecalculateResponseDto(
        int recalculatedPlayersCount,
        String strategyCode,
        String strategyName,
        int strategyVersion,
        long executionDurationMs,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime timestamp
) {}
