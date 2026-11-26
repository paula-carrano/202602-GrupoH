package com.cbo.players.service;

import com.cbo.players.dto.response.AdvancedMetricsResponseDto;
import com.cbo.players.model.AlertStatus;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.UserRole;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PortfolioPositionRepository;
import com.cbo.players.repository.PriceAlertRepository;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedMetricsServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private PlayerQuoteRepository playerQuoteRepository;
    @Mock private PortfolioPositionRepository portfolioPositionRepository;
    @Mock private ValuationStrategyRepository valuationStrategyRepository;
    @Mock private PriceAlertRepository priceAlertRepository;
    @Mock private UserRepository userRepository;

    private SimpleMeterRegistry meterRegistry;
    private AdvancedMetricsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new AdvancedMetricsService(
                transactionRepository,
                playerRepository,
                playerQuoteRepository,
                portfolioPositionRepository,
                valuationStrategyRepository,
                priceAlertRepository,
                userRepository,
                meterRegistry
        );
    }

    @Test
    @DisplayName("FR-010 - Consolida métricas de mercado, valoración, alertas, rendimiento y generatedAt en raíz")
    void getAdvancedMetrics_returnsConsolidatedSnapshot() {
        Player p1 = new Player("Kylian", "Mbappe", LocalDate.of(1998, 12, 20),
                "French", PlayerPosition.FORWARD, "Real Madrid", "La Liga");
        p1.setId(1L);
        Player p2 = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        p2.setId(2L);

        ValuationStrategy strategy = new ValuationStrategy("OFFENSIVE", "Rendimiento Ofensivo", "desc",
                2, true, BigDecimal.ONE, new ValuationWeights(1.5, 1.0, 0.01, 0.2, 0.3, 1.2, -0.3));
        LocalDateTime lastRecalc = LocalDateTime.of(2026, 11, 24, 18, 0);

        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(p1, p2));
        when(transactionRepository.countByOperationType(OperationType.COMPRA)).thenReturn(10L);
        when(transactionRepository.countByOperationType(OperationType.VENTA)).thenReturn(4L);
        when(transactionRepository.countByOperationType(OperationType.RECARGA)).thenReturn(5L);
        when(transactionRepository.countByOperationType(OperationType.CAMBIO_ESTRATEGIA)).thenReturn(1L);
        when(transactionRepository.sumTotalAmountByOperationTypes(anyList())).thenReturn(new BigDecimal("520.5000"));
        when(userRepository.countByEnabledTrue()).thenReturn(6L);
        when(portfolioPositionRepository.sumTokensExcludingUserRole(UserRole.ROLE_SUPERUSER)).thenReturn(50L);
        when(transactionRepository.findTopTradedPlayersRaw(anyList(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 35L, new BigDecimal("350.0000")}));
        when(playerRepository.findAllById(List.of(1L))).thenReturn(List.of(p1));

        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerQuoteRepository.findMaxTimestamp()).thenReturn(Optional.of(lastRecalc));
        when(playerQuoteRepository.findLatestQuotesForActivePlayers()).thenReturn(List.of(
                new PlayerQuote(p1, new BigDecimal("14.0000"), "CRD", lastRecalc),
                new PlayerQuote(p2, new BigDecimal("6.0000"), "CRD", lastRecalc)
        ));

        when(priceAlertRepository.countByStatus(AlertStatus.ACTIVA)).thenReturn(3L);
        when(priceAlertRepository.countByStatus(AlertStatus.DISPARADA)).thenReturn(2L);

        meterRegistry.counter(RankingService.METRIC_CACHE_REQUESTS, "result", "hit").increment(9);
        meterRegistry.counter(RankingService.METRIC_CACHE_REQUESTS, "result", "miss").increment(1);
        meterRegistry.timer(RankingService.METRIC_QUERY_DURATION, "source", "cache").record(4, TimeUnit.MILLISECONDS);
        meterRegistry.timer(RankingService.METRIC_QUERY_DURATION, "source", "db").record(16, TimeUnit.MILLISECONDS);

        AdvancedMetricsResponseDto dto = service.getAdvancedMetrics();

        assertThat(dto.generatedAt()).isNotNull();
        assertThat(dto.market().totalTransactions()).isEqualTo(20L);
        assertThat(dto.market().totalTradedCredits()).isEqualByComparingTo("520.5000");
        assertThat(dto.market().totalIssuedTokens()).isEqualTo(200L);
        assertThat(dto.market().tokensInCirculation()).isEqualTo(50L);
        assertThat(dto.market().circulationRatioPercent()).isEqualTo(25.0);
        assertThat(dto.market().topTradedPlayers()).hasSize(1);
        assertThat(dto.market().topTradedPlayers().get(0).playerName()).isEqualTo("Kylian Mbappe");

        assertThat(dto.valuation().activeStrategyCode()).isEqualTo("OFFENSIVE");
        assertThat(dto.valuation().activeStrategyVersion()).isEqualTo(2);
        assertThat(dto.valuation().averageQuote()).isEqualByComparingTo("10.0000");
        assertThat(dto.valuation().minQuote()).isEqualByComparingTo("6.0000");
        assertThat(dto.valuation().maxQuote()).isEqualByComparingTo("14.0000");

        assertThat(dto.alerts().activeAlerts()).isEqualTo(3L);
        assertThat(dto.alerts().triggeredAlerts()).isEqualTo(2L);

        assertThat(dto.performance().rankingCacheHits()).isEqualTo(9L);
        assertThat(dto.performance().rankingCacheMisses()).isEqualTo(1L);
        assertThat(dto.performance().rankingCacheHitRatioPercent()).isEqualTo(90.0);
        assertThat(dto.performance().avgRankingLatencyMs()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("FR-010 - Sistema vacío retorna ceros determinísticos sin división por cero")
    void getAdvancedMetrics_emptySystemHandlesZeroDivisionSafely() {
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of());
        when(transactionRepository.countByOperationType(any())).thenReturn(0L);
        when(transactionRepository.sumTotalAmountByOperationTypes(anyList())).thenReturn(null);
        when(userRepository.countByEnabledTrue()).thenReturn(0L);
        when(portfolioPositionRepository.sumTokensExcludingUserRole(UserRole.ROLE_SUPERUSER)).thenReturn(null);
        when(transactionRepository.findTopTradedPlayersRaw(anyList(), any(Pageable.class))).thenReturn(List.of());
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.empty());
        when(playerQuoteRepository.findMaxTimestamp()).thenReturn(Optional.empty());
        when(priceAlertRepository.countByStatus(any())).thenReturn(0L);

        AdvancedMetricsResponseDto dto = service.getAdvancedMetrics();

        assertThat(dto.generatedAt()).isNotNull();
        assertThat(dto.market().totalTransactions()).isZero();
        assertThat(dto.market().circulationRatioPercent()).isZero();
        assertThat(dto.market().topTradedPlayers()).isEmpty();
        assertThat(dto.valuation().coveredPlayers()).isZero();
        assertThat(dto.valuation().averageQuote()).isEqualByComparingTo("0.0000");
        assertThat(dto.valuation().lastRecalculationAt()).isNull();
        assertThat(dto.performance().rankingCacheHitRatioPercent()).isZero();
        assertThat(dto.performance().avgRankingLatencyMs()).isZero();
    }
}
