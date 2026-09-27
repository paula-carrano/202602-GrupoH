package com.cbo.players.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Browsers do not attach these credential headers automatically. JSON login
                // returns a token in the body, without creating an authenticated cookie session.
                .csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfig::usesExplicitCredentials,
                        SecurityConfig::isJsonAuthentication))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
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

    private static boolean isJsonAuthentication(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!"POST".equals(request.getMethod()) || !("/api/v1/auth/login".equals(path)
                || "/api/v1/auth/register".equals(path))) return false;
        try {
            return request.getContentType() != null && MediaType.APPLICATION_JSON
                    .includes(MediaType.parseMediaType(request.getContentType()));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
