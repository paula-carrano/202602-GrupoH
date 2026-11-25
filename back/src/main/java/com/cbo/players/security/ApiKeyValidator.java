package com.cbo.players.security;

import com.cbo.players.model.ApiKey;

import java.util.Optional;

public interface ApiKeyValidator {
    Optional<ApiKey> validateApiKey(String rawKey);
}
