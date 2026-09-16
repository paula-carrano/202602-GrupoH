package com.cbo.players.dto.response;

public record LoginResponseDto(
    String accessToken,
    String tokenType,
    long expiresInSeconds
) {}
