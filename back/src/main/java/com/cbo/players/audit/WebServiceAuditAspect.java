package com.cbo.players.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class WebServiceAuditAspect {

    public static final String AUDIT_LOGGER_NAME = "AUDIT_LOGGER";
    private static final Logger auditLog = LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
    private static final String ANONYMOUS_USER = "anonymous";

    private final AuditParameterSanitizer sanitizer;

    public WebServiceAuditAspect(AuditParameterSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    // Audit the failure while preserving the original exception for HTTP error handling.
    @SuppressWarnings("java:S2139")
    @Around("within(@org.springframework.web.bind.annotation.RestController *) && within(com.cbo.players.controller..*) && execution(public * *(..))")
    public Object auditWebServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant timestamp = Instant.now();
        long startNanos = System.nanoTime();
        String correlationId = CorrelationIdFilter.currentOrGenerate();
        String user = resolveCurrentUser();
        String operation = resolveOperation(joinPoint);
        String params = resolveSanitizedParameters(joinPoint);

        try {
            Object result = joinPoint.proceed();
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startNanos));
            auditLog.info(
                    "AUDIT | timestamp={} | correlationId={} | user={} | operation={} | params={} | executionTimeMs={} | outcome=SUCCESS",
                    timestamp, correlationId, user, operation, params, executionTimeMs);
            return result;
        } catch (Throwable ex) {
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startNanos));
            auditLog.info(
                    "AUDIT | timestamp={} | correlationId={} | user={} | operation={} | params={} | executionTimeMs={} | outcome=ERROR({})",
                    timestamp, correlationId, user, operation, params, executionTimeMs, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS_USER;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
            return ANONYMOUS_USER;
        }
        return name.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String resolveOperation(ProceedingJoinPoint joinPoint) {
        String controllerName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String methodRef = controllerName + "." + methodName;

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String httpMethod = request.getMethod();
            String uri = request.getRequestURI();
            return httpMethod + " " + uri + " (" + methodRef + ")";
        }
        return methodRef;
    }

    private String resolveSanitizedParameters(ProceedingJoinPoint joinPoint) {
        try {
            String[] paramNames = null;
            if (joinPoint.getSignature() instanceof MethodSignature methodSignature) {
                paramNames = methodSignature.getParameterNames();
            }
            return sanitizer.sanitize(paramNames, joinPoint.getArgs());
        } catch (RuntimeException ex) {
            return "{sanitizationError=" + ex.getClass().getSimpleName() + "}";
        }
    }
}
