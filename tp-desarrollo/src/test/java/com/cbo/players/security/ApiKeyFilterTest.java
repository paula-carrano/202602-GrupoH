package com.cbo.players.security;

import com.cbo.players.model.ApiKey;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_validApiKeyHeader_authenticatesUser() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "cbo_live_testkey123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        User user = new User("admin", "admin@cbo.com", "hash", UserRole.ROLE_ADMIN);
        user.setId(1L);
        ApiKey apiKey = new ApiKey(user, "Test Key", "hash", "cbo_live_");

        when(apiKeyService.validateApiKey("cbo_live_testkey123")).thenReturn(Optional.of(apiKey));

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_missingApiKeyHeader_continuesWithoutAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
