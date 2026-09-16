package com.cbo.players.service;

import com.cbo.players.dto.request.CreateApiKeyRequestDto;
import com.cbo.players.dto.response.ApiKeyCreatedResponseDto;
import com.cbo.players.model.ApiKey;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.repository.ApiKeyRepository;
import com.cbo.players.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, userRepository);
    }

    @Test
    void createApiKey_success() {
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto("App Movil");
        User user = new User("usuario123", "usuario@ejemplo.com", "passHash", UserRole.ROLE_USER);
        user.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ApiKey savedApiKey = new ApiKey(user, "App Movil", "hash64chars", "cbo_live_1234");
        savedApiKey.setId(10L);
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(savedApiKey);

        ApiKeyCreatedResponseDto response = apiKeyService.createApiKey(request, 1L);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("App Movil", response.name());
        assertNotNull(response.rawKey());
        assertTrue(response.rawKey().startsWith("cbo_live_"));
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void validateApiKey_validKey_returnsApiKey() {
        String rawKey = "cbo_live_validkey1234567890abcdef";
        String expectedHash = apiKeyService.hashKey(rawKey);

        ApiKey apiKey = new ApiKey();
        when(apiKeyRepository.findByKeyHashAndActiveTrue(expectedHash)).thenReturn(Optional.of(apiKey));

        Optional<ApiKey> result = apiKeyService.validateApiKey(rawKey);

        assertTrue(result.isPresent());
        assertSame(apiKey, result.get());
    }

    @Test
    void validateApiKey_emptyOrNull_returnsEmpty() {
        assertTrue(apiKeyService.validateApiKey(null).isEmpty());
        assertTrue(apiKeyService.validateApiKey("").isEmpty());
    }
}
