package com.cbo.players.dto.response;

import java.time.LocalDateTime;

public record ApiKeyCreatedResponseDto(
    Long id,
    String name,
    String rawKey,
    String prefix,
    LocalDateTime createdAt
) {}
