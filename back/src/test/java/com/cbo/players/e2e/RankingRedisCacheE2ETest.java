package com.cbo.players.e2e;

import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import com.cbo.players.security.JwtTokenProvider;
import com.cbo.players.service.QuoteCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RankingRedisCacheE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerQuoteRepository playerQuoteRepository;

    @Autowired
    private ValuationStrategyRepository valuationStrategyRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private QuoteCalculationService quoteCalculationService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    private User user;
    private String userToken;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        cleanupDatabase();

        user = userRepository.findByUsername("user_ranking_test")
                .orElseGet(() -> userRepository.save(new User(
                        "user_ranking_test",
                        "ranking_test@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_USER
                )));

        userToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), "ROLE_USER");

        valuationStrategyRepository.findByCode("BALANCED")
                .orElseGet(() -> valuationStrategyRepository.save(new ValuationStrategy(
                        "BALANCED",
                        "Rendimiento Global Equilibrado",
                        "Estrategia balanceada",
                        1,
                        true,
                        new BigDecimal("1.0000"),
                        1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5
                )));
        for (ValuationStrategy s : valuationStrategyRepository.findAll()) {
            s.setActive(s.getCode().equalsIgnoreCase("BALANCED"));
            valuationStrategyRepository.save(s);
        }

        player1 = playerRepository.save(new Player(
                "Erling",
                "Haaland",
                LocalDate.of(2000, 7, 21),
                "Norwegian",
                PlayerPosition.FORWARD,
                "Manchester City",
                "Premier League"
        ));

        player2 = playerRepository.save(new Player(
                "Luka",
                "Modric",
                LocalDate.of(1985, 9, 9),
                "Croatian",
                PlayerPosition.MIDFIELDER,
                "Real Madrid",
                "La Liga"
        ));

        playerQuoteRepository.save(new PlayerQuote(
                player1,
                new BigDecimal("30.0000"),
                "CRD",
                LocalDateTime.now(ZoneOffset.UTC),
                "Balanced",
                1
        ));

        playerQuoteRepository.save(new PlayerQuote(
                player2,
                new BigDecimal("15.0000"),
                "CRD",
                LocalDateTime.now(ZoneOffset.UTC),
                "Balanced",
                1
        ));

        if (cacheManager != null) {
            Cache rankingCache = cacheManager.getCache("players::ranking");
            if (rankingCache != null) {
                rankingCache.clear();
            }
        }
    }

    @Test
    @DisplayName("US6 - GET /api/players/ranking retorna ranking ordenado descendentemente")
    void getRanking_success() throws Exception {
        mockMvc.perform(get("/api/players/ranking")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyCode").value("BALANCED"))
                .andExpect(jsonPath("$.ranking", hasSize(2)))
                .andExpect(jsonPath("$.ranking[0].playerId").value(player1.getId()))
                .andExpect(jsonPath("$.ranking[0].currentQuote").value(30.0))
                .andExpect(jsonPath("$.ranking[0].rank").value(1))
                .andExpect(jsonPath("$.ranking[1].playerId").value(player2.getId()))
                .andExpect(jsonPath("$.ranking[1].currentQuote").value(15.0))
                .andExpect(jsonPath("$.ranking[1].rank").value(2));
    }

    @Test
    @DisplayName("US6 - Recálculo de cotizaciones invalida la caché de ranking")
    void recalculateQuotes_invalidatesRankingCache() {
        if (cacheManager != null) {
            Cache rankingCache = cacheManager.getCache("players::ranking");
            if (rankingCache != null) {
                rankingCache.put("active_ranking", "some_cached_data");
                assertThat(rankingCache.get("active_ranking")).isNotNull();

                quoteCalculationService.recalculateAllQuotes();

                assertThat(rankingCache.get("active_ranking")).isNull();
            }
        }
    }
}
