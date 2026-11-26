package com.cbo.players.audit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("FR-007 - Genera UUID v4 cuando no viene X-Correlation-ID y limpia MDC al finalizar")
    void doFilter_generatesCorrelationIdAndCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/players/ranking");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideMdc = new AtomicReference<>();

        FilterChain chain = (req, res) -> insideMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(insideMdc.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(insideMdc.get());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("FR-007 - Reutiliza y sanitiza X-Correlation-ID provisto por el cliente")
    void doFilter_propagatesAndSanitizesIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/buy");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "trace-abc-123\r\ninjected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> insideMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(insideMdc.get()).isEqualTo("trace-abc-123injected");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("trace-abc-123injected");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("FR-007 - Limpia MDC incluso si la cadena lanza una excepción")
    void doFilter_cleansMdcWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/metrics/advanced");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank();
            throw new IllegalStateException("downstream error");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
