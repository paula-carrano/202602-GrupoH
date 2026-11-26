package com.cbo.players.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_HEADER_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(CORRELATION_ID_HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String currentOrGenerate() {
        String existing = MDC.get(MDC_KEY);
        return StringUtils.hasText(existing) ? existing : UUID.randomUUID().toString();
    }

    private String resolveCorrelationId(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return UUID.randomUUID().toString();
        }
        String sanitized = headerValue.trim().replaceAll("[^a-zA-Z0-9._:-]", "");
        if (sanitized.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sanitized.length() > MAX_HEADER_LENGTH ? sanitized.substring(0, MAX_HEADER_LENGTH) : sanitized;
    }
}
