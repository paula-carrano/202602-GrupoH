package com.cbo.players.controller;

import com.cbo.players.audit.CorrelationIdFilter;
import com.cbo.players.dto.response.AdvancedMetricsResponseDto;
import com.cbo.players.dto.response.AlertMetricsDto;
import com.cbo.players.dto.response.MarketMetricsDto;
import com.cbo.players.dto.response.PerformanceMetricsDto;
import com.cbo.players.dto.response.ValuationMetricsDto;
import com.cbo.players.exception.GlobalExceptionHandler;
import com.cbo.players.service.AdvancedMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdvancedMetricsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdvancedMetricsService advancedMetricsService;

    @InjectMocks
    private AdvancedMetricsController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new CorrelationIdFilter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("FR-010 / FR-011 - GET /metrics/advanced responde sin prefijo /api con generatedAt en raíz y propaga X-Correlation-ID")
    void getAdvancedMetrics_returnsConsolidatedDtoAndPropagatesCorrelationId() throws Exception {
        AdvancedMetricsResponseDto snapshot = new AdvancedMetricsResponseDto(
                new MarketMetricsDto(10L, Map.of("COMPRA", 6L, "VENTA", 4L), new BigDecimal("250.0000"),
                        5L, 500L, 40L, 8.0, List.of()),
                new ValuationMetricsDto("OFFENSIVE", "Rendimiento Ofensivo", 1, null,
                        5L, new BigDecimal("4.5000"), new BigDecimal("1.0000"), new BigDecimal("9.0000")),
                new AlertMetricsDto(2L, 1L),
                new PerformanceMetricsDto(95L, 5L, 95.0, 3.2),
                Instant.parse("2026-11-25T00:00:00Z")
        );
        when(advancedMetricsService.getAdvancedMetrics()).thenReturn(snapshot);

        mockMvc.perform(get("/metrics/advanced")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "unit-trace-123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "unit-trace-123"))
                .andExpect(jsonPath("$.generatedAt").value("2026-11-25T00:00:00Z"))
                .andExpect(jsonPath("$.market.totalTransactions").value(10))
                .andExpect(jsonPath("$.valuation.activeStrategyCode").value("OFFENSIVE"))
                .andExpect(jsonPath("$.alerts.activeAlerts").value(2))
                .andExpect(jsonPath("$.performance.rankingCacheHitRatioPercent").value(95.0));
    }

    @Test
    @DisplayName("FR-010 - AccessDeniedException en /metrics/advanced retorna 403 ProblemDetail con correlationId unificado")
    void getAdvancedMetrics_accessDeniedIncludesUnifiedCorrelationId() throws Exception {
        when(advancedMetricsService.getAdvancedMetrics()).thenThrow(new AccessDeniedException("denied"));

        mockMvc.perform(get("/metrics/advanced")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "unit-trace-403")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "unit-trace-403"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.correlationId").value("unit-trace-403"));
    }
}
