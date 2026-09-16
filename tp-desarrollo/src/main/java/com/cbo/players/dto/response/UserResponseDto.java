package com.cbo.players.dto.response;

import java.time.LocalDateTime;

public record UserResponseDto(
    Long id,
    String username,
    String email,
    LocalDateTime createdAt
) {}
