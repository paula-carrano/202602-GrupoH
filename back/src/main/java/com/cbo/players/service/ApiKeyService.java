package com.cbo.players.service;

import com.cbo.players.dto.request.CreateApiKeyRequestDto;
import com.cbo.players.dto.response.ApiKeyCreatedResponseDto;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.ApiKey;
import com.cbo.players.model.User;
import com.cbo.players.repository.ApiKeyRepository;
import com.cbo.players.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "cbo_live_";
    private static final int RANDOM_BYTES_LENGTH = 24; // Generates 48 hex chars

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ApiKeyCreatedResponseDto createApiKey(CreateApiKeyRequestDto request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId, ErrorCode.USER_NOT_FOUND));

        byte[] randomBytes = new byte[RANDOM_BYTES_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);

        String rawKey = KEY_PREFIX + randomHex;
        String prefix = rawKey.substring(0, 16);
        String keyHash = hashKey(rawKey);

        ApiKey apiKey = new ApiKey(user, request.name(), keyHash, prefix);
        ApiKey saved = apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedResponseDto(
                saved.getId(),
                saved.getName(),
                rawKey,
                saved.getPrefix(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<ApiKey> validateApiKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = hashKey(rawKey);
        return apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);
    }

    public String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
