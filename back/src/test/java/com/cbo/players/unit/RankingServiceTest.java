package com.cbo.players.unit;

import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.dto.response.PlayerRankingResponseDto;
import com.cbo.players.dto.response.RankingItemDto;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import com.cbo.players.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerQuoteRepository playerQuoteRepository;

    @Mock
    private ValuationStrategyRepository valuationStrategyRepository;

    @InjectMocks
    private RankingService rankingService;

    private ValuationStrategy strategy;
    private Player player1;
    private Player player2;
    private Player player3;

    @BeforeEach
    void setUp() {
        strategy = new ValuationStrategy(
                "BALANCED",
                "Rendimiento Global Equilibrado",
                "Estrategia balanceada",
                1,
                true,
                new BigDecimal("1.0000"), new ValuationWeights(
                    1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5
            ));

        player1 = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        player1.setId(1L);

        player2 = new Player("Kylian", "Mbappe", LocalDate.of(1998, 12, 20),
                "French", PlayerPosition.FORWARD, "Real Madrid", "La Liga");
        player2.setId(2L);

        player3 = new Player("Luka", "Modric", LocalDate.of(1985, 9, 9),
                "Croatian", PlayerPosition.MIDFIELDER, "Real Madrid", "La Liga");
        player3.setId(3L);
    }

    @Test
    @DisplayName("US6 - Ranking ordena jugadores de mayor a menor cotización con numeración 1-based")
    void getRanking_ordersDescendingByQuote() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2, player3));

        PlayerQuote quote1 = new PlayerQuote(player1, new BigDecimal("27.4000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);
        PlayerQuote quote2 = new PlayerQuote(player2, new BigDecimal("32.5000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);
        PlayerQuote quote3 = new PlayerQuote(player3, new BigDecimal("12.0000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);

        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(1L)).thenReturn(Optional.of(quote1));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(2L)).thenReturn(Optional.of(quote2));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(3L)).thenReturn(Optional.of(quote3));

        PlayerRankingResponseDto result = rankingService.getRanking();

        assertThat(result).isNotNull();
        assertThat(result.strategyCode()).isEqualTo("BALANCED");
        assertThat(result.ranking()).hasSize(3);

        List<RankingItemDto> ranking = result.ranking();
        assertThat(ranking.get(0).rank()).isEqualTo(1);
        assertThat(ranking.get(0).playerId()).isEqualTo(2L);
        assertThat(ranking.get(0).currentQuote()).isEqualByComparingTo("32.5000");

        assertThat(ranking.get(1).rank()).isEqualTo(2);
        assertThat(ranking.get(1).playerId()).isEqualTo(1L);
        assertThat(ranking.get(1).currentQuote()).isEqualByComparingTo("27.4000");

        assertThat(ranking.get(2).rank()).isEqualTo(3);
        assertThat(ranking.get(2).playerId()).isEqualTo(3L);
        assertThat(ranking.get(2).currentQuote()).isEqualByComparingTo("12.0000");
    }

    @Test
    @DisplayName("US6 - Jugador sin cotizaciones registradas toma valor nominal de 1.0")
    void getRanking_defaultQuoteWhenMissing() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerRepository.findAll()).thenReturn(List.of(player1));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(1L)).thenReturn(Optional.empty());

        PlayerRankingResponseDto result = rankingService.getRanking();

        assertThat(result.ranking()).hasSize(1);
        assertThat(result.ranking().get(0).currentQuote()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("US6 - Sin estrategia activa o con versión nula utiliza valores por defecto y maneja jugador sin posición")
    void getRanking_defaultsWhenNoActiveStrategyOrNullVersion() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.empty());

        Player playerWithoutPosition = new Player("Mystery", "Player", LocalDate.of(1995, 5, 5),
                "Unknown", null, "Free Agent", "None");
        playerWithoutPosition.setId(99L);
        when(playerRepository.findAll()).thenReturn(List.of(playerWithoutPosition));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(99L)).thenReturn(Optional.empty());

        PlayerRankingResponseDto resultNoStrategy = rankingService.getRanking();

        assertThat(resultNoStrategy.strategyCode()).isEqualTo("BALANCED");
        assertThat(resultNoStrategy.strategyVersion()).isEqualTo(1);
        assertThat(resultNoStrategy.ranking()).hasSize(1);
        assertThat(resultNoStrategy.ranking().get(0).position()).isEmpty();

        // Estrategia activa con versión nula
        ValuationStrategy strategyNullVersion = mock(ValuationStrategy.class);
        when(strategyNullVersion.getCode()).thenReturn("OFFENSIVE");
        when(strategyNullVersion.getVersion()).thenReturn(null);
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategyNullVersion));

        PlayerRankingResponseDto resultNullVersion = rankingService.getRanking();
        assertThat(resultNullVersion.strategyCode()).isEqualTo("OFFENSIVE");
        assertThat(resultNullVersion.strategyVersion()).isEqualTo(1);
    }
}
