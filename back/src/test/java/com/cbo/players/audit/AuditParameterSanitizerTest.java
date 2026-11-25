package com.cbo.players.audit;

import com.cbo.players.dto.request.LoginRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditParameterSanitizerTest {

    private final AuditParameterSanitizer sanitizer = new AuditParameterSanitizer();

    @Test
    @DisplayName("FR-006 - Enmascara contraseñas dentro de DTOs y parámetros sensibles directos")
    void sanitize_masksSensitiveFieldsAndDirectParameters() {
        LoginRequestDto loginDto = new LoginRequestDto("admin@cbo.com", "SuperSecret123!");
        String output = sanitizer.sanitize(
                new String[]{"request", "apiKey", "jwtToken", "authorization"},
                new Object[]{loginDto, "plain-api-key-999", "eyJhbGciOiJIUzI1NiJ9.secret", "Bearer xyz"}
        );

        assertThat(output)
                .contains("admin@cbo.com")
                .contains("password=[REDACTED]")
                .contains("apiKey=[REDACTED]")
                .contains("jwtToken=[REDACTED]")
                .contains("authorization=[REDACTED]")
                .doesNotContain("SuperSecret123!")
                .doesNotContain("plain-api-key-999")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .doesNotContain("Bearer xyz");
    }

    @Test
    @DisplayName("FR-006 - Maneja nulos, omite HttpServletRequest, sanitiza CRLF y trunca payloads extensos")
    void sanitize_handlesNullsOmittedTypesCrlfAndTruncation() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("clientSecret", "top-secret");
        nested.put("note", "line1\nline2\rline3");
        nested.put("items", List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"));

        String output = sanitizer.sanitize(
                new String[]{"req", "nullable", "payload", "longText"},
                new Object[]{req, null, nested, "x".repeat(700)}
        );

        assertThat(output)
                .doesNotContain("top-secret")
                .contains("clientSecret=[REDACTED]")
                .contains("nullable=null")
                .doesNotContain("req=")
                .doesNotContain("\n")
                .doesNotContain("\r")
                .endsWith("...[TRUNCATED]");
    }

    @Test
    @DisplayName("FR-006 - Sin argumentos devuelve objeto vacío")
    void sanitize_emptyArgsReturnsEmptyBraces() {
        assertThat(sanitizer.sanitize(null, null)).isEqualTo("{}");
        assertThat(sanitizer.sanitize(new String[0], new Object[0])).isEqualTo("{}");
    }
}
