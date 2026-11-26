package com.cbo.players.dto.response;

public record AlertMetricsDto(
        long activeAlerts,
        long triggeredAlerts
) {
}
