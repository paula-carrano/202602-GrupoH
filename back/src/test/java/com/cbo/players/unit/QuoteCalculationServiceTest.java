package com.cbo.players.unit;

import com.cbo.players.dto.response.QuoteRecalculateResponseDto;
import com.cbo.players.event.QuotesRecalculatedEvent;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PlayerStatisticsRepository;
import com.cbo.players.service.QuoteCalculationService;
import com.cbo.players.service.ValuationStrategyService;
import com.cbo.players.service.formula.ValuationFormula;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteCalculationServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerStatisticsRepository playerStatisticsRepository;

    @Mock
    private PlayerQuoteRepository playerQuoteRepository;

    @Mock
    private ValuationStrategyService valuationStrategyService;

    @Mock
    private ValuationFormula valuationFormula;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CacheManager cacheManager;

    private ValuationStrategy activeStrategy;
    private Player activePlayer;
    private Player inactivePlayer;

    @BeforeEach
    void setUp() {
        activeStrategy = new ValuationStrategy(
                "BALANCED", "Rendimiento Equilibrado", "Desc",
                1, true, new BigDecimal("1.0000"),
                1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5
        );
        activeStrategy.setId(1L);

        activePlayer = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        activePlayer.setId(10L);
        activePlayer.setActive(true);

        inactivePlayer = new Player("Retired", "Player", LocalDate.of(1970, 1, 1),
                "Unknown", PlayerPosition.GOALKEEPER, "None", "None");
        inactivePlayer.setId(11L);
        inactivePlayer.setActive(false);
    }

    @Test
    @DisplayName("recalculateAllQuotes procesa jugadores activos, guarda cotizaciones, emite evento y limpia cachés")
    void recalculateAllQuotes_success() {
        when(valuationStrategyService.getActiveStrategy()).thenReturn(activeStrategy);
        when(valuationFormula.getStrategyCode()).thenReturn("BALANCED");
        when(valuationFormula.calculate(any(), eq(activeStrategy))).thenReturn(new BigDecimal("42.5000"));

        when(playerRepository.findAll()).thenReturn(List.of(activePlayer, inactivePlayer));
        PlayerStatistics stats = mock(PlayerStatistics.class);
        when(playerStatisticsRepository.findById(10L)).thenReturn(Optional.of(stats));

        Cache rankingCache = mock(Cache.class);
        Cache catalogCache = mock(Cache.class);
        when(cacheManager.getCache("players::ranking")).thenReturn(rankingCache);
        when(cacheManager.getCache("players::catalog")).thenReturn(catalogCache);

        QuoteCalculationService service = new QuoteCalculationService(
                valuationStrategyService, List.of(valuationFormula),
                playerRepository, playerStatisticsRepository, playerQuoteRepository,
                eventPublisher, cacheManager
        );

        QuoteRecalculateResponseDto response = service.recalculateAllQuotes();

        assertThat(response).isNotNull();
        assertThat(response.recalculatedPlayersCount()).isEqualTo(1);
        assertThat(response.strategyCode()).isEqualTo("BALANCED");
        assertThat(response.strategyVersion()).isEqualTo(1);

        verify(playerQuoteRepository).saveAll(anyList());
        verify(eventPublisher).publishEvent(any(QuotesRecalculatedEvent.class));
        verify(rankingCache).clear();
        verify(catalogCache).clear();
    }

    @Test
    @DisplayName("recalculateAllQuotes lanza ApiException 500 si no existe fórmula para la estrategia activa")
    void recalculateAllQuotes_throwsWhenFormulaNotFound() {
        when(valuationStrategyService.getActiveStrategy()).thenReturn(activeStrategy);
        when(valuationFormula.getStrategyCode()).thenReturn("OTHER_STRATEGY");

        QuoteCalculationService service = new QuoteCalculationService(
                valuationStrategyService, List.of(valuationFormula),
                playerRepository, playerStatisticsRepository, playerQuoteRepository,
                eventPublisher, cacheManager
        );

        assertThatThrownBy(service::recalculateAllQuotes)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
                });

        verify(playerQuoteRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("recalculateAllQuotes con lista vacía de jugadores activos no guarda cotizaciones pero emite evento")
    void recalculateAllQuotes_withNoActivePlayersDoesNotSaveQuotes() {
        when(valuationStrategyService.getActiveStrategy()).thenReturn(activeStrategy);
        when(valuationFormula.getStrategyCode()).thenReturn("BALANCED");
        when(playerRepository.findAll()).thenReturn(List.of(inactivePlayer));

        QuoteCalculationService service = new QuoteCalculationService(
                valuationStrategyService, List.of(valuationFormula),
                playerRepository, playerStatisticsRepository, playerQuoteRepository,
                eventPublisher, null
        );

        QuoteRecalculateResponseDto response = service.recalculateAllQuotes();

        assertThat(response.recalculatedPlayersCount()).isZero();
        verify(playerQuoteRepository, never()).saveAll(anyList());
        verify(eventPublisher).publishEvent(any(QuotesRecalculatedEvent.class));
    }

    @Test
    @DisplayName("evictCaches tolera errores lanzados por la caché registrando warning sin fallar el proceso")
    void evictCaches_handlesCacheClearExceptionGracefully() {
        when(valuationStrategyService.getActiveStrategy()).thenReturn(activeStrategy);
        when(valuationFormula.getStrategyCode()).thenReturn("BALANCED");
        when(playerRepository.findAll()).thenReturn(Collections.emptyList());

        Cache faultyCache = mock(Cache.class);
        doThrow(new RuntimeException("Redis connection refused")).when(faultyCache).clear();
        when(cacheManager.getCache("players::ranking")).thenReturn(faultyCache);

        QuoteCalculationService service = new QuoteCalculationService(
                valuationStrategyService, List.of(valuationFormula),
                playerRepository, playerStatisticsRepository, playerQuoteRepository,
                eventPublisher, cacheManager
        );

        // No debe lanzar excepción
        QuoteRecalculateResponseDto response = service.recalculateAllQuotes();
        assertThat(response).isNotNull();
    }
}
