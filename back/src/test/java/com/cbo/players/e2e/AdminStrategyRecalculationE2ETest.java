package com.cbo.players.e2e;

import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import com.cbo.players.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminStrategyRecalculationE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerStatisticsRepository playerStatisticsRepository;

    @Autowired
    private PlayerQuoteRepository playerQuoteRepository;

    @Autowired
    private ValuationStrategyRepository valuationStrategyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.cbo.players.service.CatalogPersistenceService catalogPersistenceService;

    private String superuserToken;
    private String regularUserToken;
    private String adminToken;

    private ValuationStrategy offensiveStrategy;
    private ValuationStrategy balancedStrategy;
    private Player activePlayerWithStats;
    private Player activePlayerZeroStats;
    private Player inactivePlayer;

    @BeforeEach
    void setUp() {
        cleanupDatabase();

        // 1. Setup Users
        User superuser = userRepository.findByUsername("super_admin_test")
                .orElseGet(() -> userRepository.save(new User(
                        "super_admin_test",
                        "superuser@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_SUPERUSER
                )));

        User regularUser = userRepository.findByUsername("regular_user_test")
                .orElseGet(() -> userRepository.save(new User(
                        "regular_user_test",
                        "user@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_USER
                )));

        User adminUser = userRepository.findByUsername("admin_user_test")
                .orElseGet(() -> userRepository.save(new User(
                        "admin_user_test",
                        "admin@test.com",
                        passwordEncoder.encode("Pass123!"),
                        UserRole.ROLE_ADMIN
                )));

        superuserToken = jwtTokenProvider.generateToken(superuser.getUsername(), superuser.getId(), "ROLE_SUPERUSER");
        regularUserToken = jwtTokenProvider.generateToken(regularUser.getUsername(), regularUser.getId(), "ROLE_USER");
        adminToken = jwtTokenProvider.generateToken(adminUser.getUsername(), adminUser.getId(), "ROLE_ADMIN");

        // 2. Setup Strategies
        valuationStrategyRepository.deleteAllInBatch();

        offensiveStrategy = valuationStrategyRepository.save(new ValuationStrategy(
                "OFFENSIVE",
                "Rendimiento Ofensivo",
                "Estrategia de ataque",
                1,
                true,
                new BigDecimal("1.0000"),
                1.5, 1.0, 0.005, 0.2, 0.3, 1.2, -0.3
        ));

        balancedStrategy = valuationStrategyRepository.save(new ValuationStrategy(
                "BALANCED",
                "Rendimiento Global Equilibrado",
                "Estrategia balanceada",
                1,
                false,
                new BigDecimal("1.0000"),
                1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5
        ));

        // 3. Setup Players & Statistics
        Player haaland = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21), "Norway",
                PlayerPosition.FORWARD, "Manchester City", "Premier League");
        haaland.setActive(true);
        haaland.setWhoscoredId(101L);
        haaland = playerRepository.saveAndFlush(haaland);

        catalogPersistenceService.saveStatistics(
                new com.cbo.players.service.CatalogPersistenceService.LinkedPlayer(haaland.getId(), 101L),
                new com.cbo.players.adapter.dto.PlayerStatsDto(15, 5, 40, 20, 10, 5, 8.2, 1100, 1, 0)
        );
        activePlayerWithStats = haaland;

        Player rookie = new Player("Joven", "Promesa", LocalDate.of(2006, 1, 1), "Argentina",
                PlayerPosition.MIDFIELDER, "River Plate", "Liga Profesional");
        rookie.setActive(true);
        rookie.setWhoscoredId(102L);
        rookie = playerRepository.saveAndFlush(rookie);

        catalogPersistenceService.saveStatistics(
                new com.cbo.players.service.CatalogPersistenceService.LinkedPlayer(rookie.getId(), 102L),
                new com.cbo.players.adapter.dto.PlayerStatsDto(0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0)
        );
        activePlayerZeroStats = rookie;

        Player inactive = new Player("Retirado", "Jugador", LocalDate.of(1980, 1, 1), "Uruguay",
                PlayerPosition.DEFENDER, "Sin Club", "Ninguna");
        inactive.setActive(false);
        inactivePlayer = playerRepository.saveAndFlush(inactive);
    }

    @Test
    @DisplayName("PATCH /api/admin/strategies/{id}/activate como SUPERUSER debe activar la estrategia (200)")
    void activateStrategyAsSuperuserSuccess() throws Exception {
        mockMvc.perform(patch("/api/admin/strategies/" + balancedStrategy.getId() + "/activate")
                        .header("Authorization", "Bearer " + superuserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(balancedStrategy.getId()))
                .andExpect(jsonPath("$.code").value("BALANCED"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.weights.goals").value(1.0))
                .andExpect(jsonPath("$.weights.assists").value(0.8));

        // Verificar que en base de datos la estrategia cambió
        ValuationStrategy active = valuationStrategyRepository.findByActiveTrue().orElseThrow();
        assertThat(active.getId()).isEqualTo(balancedStrategy.getId());
        assertThat(active.getCode()).isEqualTo("BALANCED");

        ValuationStrategy previous = valuationStrategyRepository.findById(offensiveStrategy.getId()).orElseThrow();
        assertThat(previous.isActive()).isFalse();

        // Verificar registro auditable en transactions
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).isNotEmpty();
        assertThat(transactions.stream().anyMatch(t -> t.getOperationType() == OperationType.CAMBIO_ESTRATEGIA)).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/admin/strategies/{id}/activate como usuario regular debe retornar 403 Forbidden")
    void activateStrategyAsRegularUserReturns403() throws Exception {
        mockMvc.perform(patch("/api/admin/strategies/" + balancedStrategy.getId() + "/activate")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/strategies/{id}/activate con ID inexistente debe retornar 404 Not Found")
    void activateStrategyNotFoundReturns404() throws Exception {
        mockMvc.perform(patch("/api/admin/strategies/99999/activate")
                        .header("Authorization", "Bearer " + superuserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("STRATEGY_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/quotes/recalculate como SUPERUSER debe recalcular jugadores activos y persistir PlayerQuote")
    void recalculateQuotesAsSuperuserSuccess() throws Exception {
        mockMvc.perform(post("/api/quotes/recalculate")
                        .header("Authorization", "Bearer " + superuserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recalculatedPlayersCount").value(2))
                .andExpect(jsonPath("$.strategyCode").value("OFFENSIVE"))
                .andExpect(jsonPath("$.strategyName").value("Rendimiento Ofensivo"))
                .andExpect(jsonPath("$.strategyVersion").value(1))
                .andExpect(jsonPath("$.executionDurationMs").isNumber())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        // Verificar cotizaciones persistidas
        List<PlayerQuote> haalandQuotes = playerQuoteRepository.findByPlayerIdOrderByTimestampDesc(activePlayerWithStats.getId());
        assertThat(haalandQuotes).hasSize(1);
        PlayerQuote haalandQuote = haalandQuotes.get(0);
        assertThat(haalandQuote.getPrice()).isGreaterThan(new BigDecimal("1.0000"));
        assertThat(haalandQuote.getStrategyName()).isEqualTo("Rendimiento Ofensivo");
        assertThat(haalandQuote.getStrategyVersion()).isEqualTo(1);
        assertThat(haalandQuote.getCurrency()).isEqualTo("CRD");

        // Jugador sin minutos debe recibir minQuote (1.0000)
        List<PlayerQuote> zeroStatsQuotes = playerQuoteRepository.findByPlayerIdOrderByTimestampDesc(activePlayerZeroStats.getId());
        assertThat(zeroStatsQuotes).hasSize(1);
        assertThat(zeroStatsQuotes.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("1.0000"));

        // Jugador inactivo no debe tener cotización calculada
        List<PlayerQuote> inactiveQuotes = playerQuoteRepository.findByPlayerIdOrderByTimestampDesc(inactivePlayer.getId());
        assertThat(inactiveQuotes).isEmpty();
    }

    @Test
    @DisplayName("Flujo integrado: Activar nueva estrategia y luego recalcular cotizaciones")
    void activateStrategyAndRecalculateFlow() throws Exception {
        // 1. Superuser activa BALANCED
        mockMvc.perform(patch("/api/admin/strategies/" + balancedStrategy.getId() + "/activate")
                        .header("Authorization", "Bearer " + superuserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BALANCED"));

        // 2. Ejecutar recálculo
        mockMvc.perform(post("/api/quotes/recalculate")
                        .header("Authorization", "Bearer " + superuserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyCode").value("BALANCED"))
                .andExpect(jsonPath("$.strategyName").value("Rendimiento Global Equilibrado"));

        // 3. Verificar que las nuevas cotizaciones tengan la nueva estrategia
        List<PlayerQuote> quotes = playerQuoteRepository.findByPlayerIdOrderByTimestampDesc(activePlayerWithStats.getId());
        assertThat(quotes).hasSize(1);
        assertThat(quotes.get(0).getStrategyName()).isEqualTo("Rendimiento Global Equilibrado");
    }

    @Test
    @DisplayName("POST /api/quotes/recalculate como usuario regular debe retornar 403 Forbidden")
    void recalculateQuotesAsRegularUserReturns403() throws Exception {
        mockMvc.perform(post("/api/quotes/recalculate")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
