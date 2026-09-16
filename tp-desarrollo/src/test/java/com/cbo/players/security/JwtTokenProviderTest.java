package com.cbo.players.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "dGhpc19pc19hX3Zlcnlfc2VjdXJlX2tleV9mb3Jfand0X3NpZ25pbmdfMjU2X2JpdHNfc2VjcmV0";
    private final long expirationMs = 3600000;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(secret, expirationMs);
    }

    @Test
    void generateToken_and_validateToken_success() {
        String token = tokenProvider.generateToken("messi", 10L, "ROLE_USER");

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(tokenProvider.validateToken(token));
        assertEquals("messi", tokenProvider.getUsernameFromToken(token));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(tokenProvider.validateToken("invalid.jwt.token"));
    }
}
