package com.cbo.players.security;

import com.cbo.players.audit.CorrelationIdFilter;
import com.cbo.players.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;
    private static final String ROLE_SUPERUSER = "SUPERUSER";

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            ObjectMapper objectMapper,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOriginsProperty) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/metrics/**", configuration);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Safe because this application is a stateless REST API.
                // Authentication is performed through Authorization: Bearer JWT
                // or X-API-Key headers, never through authentication cookies.
                // Login/register receive JSON credentials and do not establish
                // browser sessions.
                .csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfig::usesExplicitCredentials,
                        SecurityConfig::isJsonAuthentication))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Autenticación requerida para acceder a este recurso.");
                            problem.setInstance(URI.create(request.getRequestURI()));
                            problem.setProperty("code", ErrorCode.UNAUTHORIZED_ACCESS.name());
                            problem.setProperty("errorCode", ErrorCode.UNAUTHORIZED_ACCESS.name());
                            problem.setProperty("timestamp", Instant.now().toString());
                            problem.setProperty("correlationId", CorrelationIdFilter.currentOrGenerate());

                            objectMapper.writeValue(response.getWriter(), problem);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                            String uri = request.getRequestURI();
                            String detail;
                            if (uri.startsWith("/api/admin/strategies")) {
                                detail = "Acceso denegado: se requieren permisos exclusivos de superusuario para activar estrategias de mercado.";
                            } else if (uri.startsWith("/metrics")) {
                                detail = "Acceso denegado: se requieren permisos exclusivos de superusuario para consultar métricas avanzadas.";
                            } else {
                                detail = "Acceso denegado: se requieren permisos exclusivos para realizar esta operación.";
                            }

                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
                            problem.setInstance(URI.create(uri));
                            problem.setProperty("code", ErrorCode.ACCESS_DENIED.name());
                            problem.setProperty("errorCode", ErrorCode.ACCESS_DENIED.name());
                            problem.setProperty("timestamp", Instant.now().toString());
                            problem.setProperty("correlationId", CorrelationIdFilter.currentOrGenerate());

                            objectMapper.writeValue(response.getWriter(), problem);
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/strategies/**").hasRole(ROLE_SUPERUSER)
                        .requestMatchers("/metrics/**").hasRole(ROLE_SUPERUSER)
                        .requestMatchers(HttpMethod.POST, "/api/quotes/recalculate").hasAnyRole("ADMIN", ROLE_SUPERUSER)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/metrics",
                                "/actuator/metrics/**",
                                "/actuator/prometheus",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static boolean usesExplicitCredentials(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return (authorization != null && authorization.startsWith("Bearer ")
                && StringUtils.hasText(authorization.substring(7)))
                || StringUtils.hasText(request.getHeader("X-API-Key"));
    }

    @SuppressWarnings("java:S7467") // Java 17 does not support unnamed catch variables.
    private static boolean isJsonAuthentication(HttpServletRequest request) {
        String path = StringUtils.hasText(request.getServletPath()) ? request.getServletPath()
                : request.getRequestURI();
        if (!"POST".equals(request.getMethod()) || !("/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)))
            return false;
        try {
            return request.getContentType() != null && MediaType.APPLICATION_JSON
                    .includes(MediaType.parseMediaType(request.getContentType()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
