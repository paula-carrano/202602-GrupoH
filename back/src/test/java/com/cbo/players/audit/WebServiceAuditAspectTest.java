package com.cbo.players.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cbo.players.dto.request.LoginRequestDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebServiceAuditAspectTest {

    private WebServiceAuditAspect aspect;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        aspect = new WebServiceAuditAspect(new AuditParameterSanitizer());
        auditLogger = (Logger) LoggerFactory.getLogger(WebServiceAuditAspect.AUDIT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    @DisplayName("FR-005 / SC-003 - Registra auditoría completa de invocación exitosa con usuario autenticado y correlationId")
    void auditWebServiceCall_logsSuccessWithAuthenticatedUser() throws Throwable {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1001");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@cbo.com", "N/A", List.of()));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/players/ranking");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ProceedingJoinPoint pjp = mockJoinPoint("PlayerController", "getRanking", new String[0], new Object[0]);
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.auditWebServiceCall(pjp);

        assertThat(result).isEqualTo("ok");
        assertThat(appender.list).hasSize(1);
        String msg = appender.list.get(0).getFormattedMessage();
        assertThat(msg)
                .contains("AUDIT |")
                .contains("timestamp=")
                .contains("correlationId=corr-1001")
                .contains("user=admin@cbo.com")
                .contains("operation=GET /players/ranking (PlayerController.getRanking)")
                .contains("params={}")
                .contains("executionTimeMs=")
                .contains("outcome=SUCCESS");
    }

    @Test
    @DisplayName("FR-006 / SC-003 - Invocación anónima con credenciales enmascara password y usa user=anonymous")
    void auditWebServiceCall_masksCredentialsForAnonymousUser() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        LoginRequestDto loginDto = new LoginRequestDto("user@cbo.com", "MySecretPass99");
        ProceedingJoinPoint pjp = mockJoinPoint("AuthController", "login", new String[]{"request"}, new Object[]{loginDto});
        when(pjp.proceed()).thenReturn("jwt");

        aspect.auditWebServiceCall(pjp);

        assertThat(appender.list).hasSize(1);
        String msg = appender.list.get(0).getFormattedMessage();
        assertThat(msg)
                .contains("user=anonymous")
                .contains("password=[REDACTED]")
                .doesNotContain("MySecretPass99")
                .contains("outcome=SUCCESS");
    }

    @Test
    @DisplayName("FR-005 - Registra outcome=ERROR y relanza excepción cuando falla el endpoint")
    void auditWebServiceCall_logsErrorAndRethrowsException() throws Throwable {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-err-1");
        ProceedingJoinPoint pjp = mockJoinPoint("OrderController", "buyTokens", new String[]{"playerId"}, new Object[]{42L});
        when(pjp.proceed()).thenThrow(new IllegalStateException("saldo insuficiente"));

        assertThatThrownBy(() -> aspect.auditWebServiceCall(pjp))
                .isInstanceOf(IllegalStateException.class);

        assertThat(appender.list).hasSize(1);
        String msg = appender.list.get(0).getFormattedMessage();
        assertThat(msg)
                .contains("correlationId=corr-err-1")
                .contains("params={playerId=42}")
                .contains("executionTimeMs=")
                .contains("outcome=ERROR(IllegalStateException)");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ProceedingJoinPoint mockJoinPoint(String controllerSimpleName, String methodName, String[] paramNames, Object[] args) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        Class declaringType = "AuthController".equals(controllerSimpleName)
                ? com.cbo.players.controller.AuthController.class
                : ("OrderController".equals(controllerSimpleName)
                ? com.cbo.players.controller.OrderController.class
                : com.cbo.players.controller.PlayerController.class);
        when(sig.getDeclaringType()).thenReturn(declaringType);
        when(sig.getName()).thenReturn(methodName);
        when(sig.getParameterNames()).thenReturn(paramNames);
        when(pjp.getArgs()).thenReturn(args);
        return pjp;
    }
}
