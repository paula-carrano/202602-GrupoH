package com.cbo.players.e2e;

import com.cbo.players.audit.CorrelationIdFilter;
import com.cbo.players.config.RedisCacheConfig;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import com.cbo.players.security.JwtTokenProvider;
import com.cbo.players.service.RankingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureObservability
class ObservabilityAndMetricsE2ETest extends AbstractE2ETest {

    @Autowired private UserRepository userRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private PlayerQuoteRepository playerQuoteRepository;
    @Autowired private ValuationStrategyRepository valuationStrategyRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired(required = false) private CacheManager cacheManager;

    private String regularUserToken;
    private String superuserToken;
    private ValuationStrategy balancedStrategy;
    private ValuationStrategy offensiveStrategy;

    @BeforeEach
    void setUp() {
        cleanupDatabase();

        User regularUser = userRepository.findByUsername("obs_user")
                .orElseGet(() -> userRepository.save(new User(
                        "obs_user", "obs_user@test.com", passwordEncoder.encode("Pass123!"), UserRole.ROLE_USER)));
        User superUser = userRepository.findByUsername("obs_superuser")
                .orElseGet(() -> userRepository.save(new User(
                        "obs_superuser", "obs_super@test.com", passwordEncoder.encode("Pass123!"), UserRole.ROLE_SUPERUSER)));

        regularUserToken = jwtTokenProvider.generateToken(regularUser.getUsername(), regularUser.getId(), "ROLE_USER");
        superuserToken = jwtTokenProvider.generateToken(superUser.getUsername(), superUser.getId(), "ROLE_SUPERUSER");

        balancedStrategy = valuationStrategyRepository.findByCode("BALANCED")
                .orElseGet(() -> valuationStrategyRepository.save(new ValuationStrategy(
                        "BALANCED", "Rendimiento Global Equilibrado", "desc", 1, true,
                        BigDecimal.ONE, new ValuationWeights(1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5))));
        offensiveStrategy = valuationStrategyRepository.findByCode("OFFENSIVE")
                .orElseGet(() -> valuationStrategyRepository.save(new ValuationStrategy(
                        "OFFENSIVE", "Rendimiento Ofensivo", "desc", 1, false,
                        BigDecimal.ONE, new ValuationWeights(1.5, 1.0, 0.01, 0.2, 0.3, 1.2, -0.3))));
        for (ValuationStrategy s : valuationStrategyRepository.findAll()) {
            s.setActive(s.getCode().equalsIgnoreCase("BALANCED"));
            valuationStrategyRepository.save(s);
        }

        Player p1 = playerRepository.save(new Player(
                "Kylian", "Mbappe", LocalDate.of(1998, 12, 20), "French",
                PlayerPosition.FORWARD, "Real Madrid", "La Liga"));
        playerQuoteRepository.save(new PlayerQuote(
                p1, new BigDecimal("25.0000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1));

        if (cacheManager != null) {
            try {
                Cache ranking = cacheManager.getCache(RedisCacheConfig.CACHE_RANKING);
                if (ranking != null) {
                    ranking.clear();
                }
            } catch (RuntimeException ignored) {
                // Redis may be intentionally unavailable in isolated E2E runs
            }
        }
    }

    @Test
    @DisplayName("FR-008 / FR-009 / SC-005 - Actuator health, info, metrics y prometheus responden 200 OK públicamente")
    void actuatorAndPrometheusEndpointsArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.version").value("3.0.0"));

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());

        // Disparar una consulta de ranking para garantizar registro de métrica custom cbo_*
        mockMvc.perform(get("/api/players/ranking")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http_server_requests")))
                .andExpect(content().string(containsString("cbo_ranking_cache_requests_total")));
    }

    @Test
    @DisplayName("FR-010 / FR-011 - GET /metrics/advanced aplica seguridad SUPERUSER (401 / 403 / 200) y propaga X-Correlation-ID")
    void advancedMetricsEndpointEnforcesSuperuserAndPropagatesCorrelationId() throws Exception {
        String traceId = "e2e-trace-metrics-001";

        // 401 sin credenciales
        mockMvc.perform(get("/metrics/advanced")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, traceId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, traceId))
                .andExpect(jsonPath("$.correlationId").value(traceId))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));

        // 403 usuario regular
        mockMvc.perform(get("/metrics/advanced")
                        .header("Authorization", "Bearer " + regularUserToken)
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, traceId))
                .andExpect(status().isForbidden())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, traceId))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.correlationId").value(traceId));

        // Preflight CORS permitido para frontend local (Vite dev server)
        mockMvc.perform(options("/metrics/advanced")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        // 200 superusuario con estructura completa y generatedAt en nivel raíz
        mockMvc.perform(get("/metrics/advanced")
                        .header("Authorization", "Bearer " + superuserToken)
                        .header("Origin", "http://localhost:5173")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, traceId))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, traceId))
                .andExpect(jsonPath("$.generatedAt", notNullValue()))
                .andExpect(jsonPath("$.market.totalIssuedTokens").value(100))
                .andExpect(jsonPath("$.valuation.activeStrategyCode").value("BALANCED"))
                .andExpect(jsonPath("$.valuation.coveredPlayers").value(1))
                .andExpect(jsonPath("$.alerts.activeAlerts").value(0))
                .andExpect(jsonPath("$.performance.rankingCacheHitRatioPercent").isNumber());
    }

    @Test
    @DisplayName("SC-001 / SC-002 - Ranking sobre Redis logra hit ratio > 90%, latencia < 50ms e invalida al cambiar estrategia")
    void rankingAndCatalogRemainResilientAndConsistentOnStrategySwitch() throws Exception {
        double hitsBefore = counterCount("hit");
        double missesBefore = counterCount("miss");
        double cacheTotalMsBefore = timerTotalMs("cache");
        long cacheCountBefore = timerCount("cache");

        // Primera lectura (cache miss) + 12 lecturas repetidas servidas desde Redis (cache hit)
        mockMvc.perform(get("/api/players/ranking")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyCode").value("BALANCED"));

        int repeatedHits = 12;
        for (int i = 0; i < repeatedHits; i++) {
            mockMvc.perform(get("/api/players/ranking")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.strategyCode").value("BALANCED"));
        }

        double deltaHits = counterCount("hit") - hitsBefore;
        double deltaMisses = counterCount("miss") - missesBefore;
        double hitRatio = (deltaHits / (deltaHits + deltaMisses)) * 100.0;
        long deltaCacheSamples = timerCount("cache") - cacheCountBefore;
        double avgCacheLatencyMs = deltaCacheSamples > 0
                ? (timerTotalMs("cache") - cacheTotalMsBefore) / deltaCacheSamples
                : Double.MAX_VALUE;

        assertThat(deltaMisses).isEqualTo(1.0);
        assertThat(deltaHits).isEqualTo(repeatedHits);
        assertThat(hitRatio).isGreaterThan(90.0);
        assertThat(deltaCacheSamples).isEqualTo(repeatedHits);
        assertThat(avgCacheLatencyMs).isLessThan(50.0);

        mockMvc.perform(get("/api/players")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Kylian"));

        // Cambiar estrategia activa como superusuario e inmediatamente consultar ranking
        mockMvc.perform(patch("/api/admin/strategies/{id}/activate", offensiveStrategy.getId())
                        .header("Authorization", "Bearer " + superuserToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/players/ranking")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyCode").value("OFFENSIVE"));
    }

    private double counterCount(String resultTag) {
        var counter = meterRegistry.find(RankingService.METRIC_CACHE_REQUESTS).tag("result", resultTag).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private long timerCount(String sourceTag) {
        var timer = meterRegistry.find(RankingService.METRIC_QUERY_DURATION).tag("source", sourceTag).timer();
        return timer != null ? timer.count() : 0L;
    }

    private double timerTotalMs(String sourceTag) {
        var timer = meterRegistry.find(RankingService.METRIC_QUERY_DURATION).tag("source", sourceTag).timer();
        return timer != null ? timer.totalTime(TimeUnit.MILLISECONDS) : 0.0;
    }
}
