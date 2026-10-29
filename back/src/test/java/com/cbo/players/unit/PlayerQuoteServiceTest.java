package com.cbo.players.unit;

import com.cbo.players.dto.response.DateEffectiveQuoteResponseDto;
import com.cbo.players.dto.response.PagedResponseDto;
import com.cbo.players.dto.response.PlayerQuoteResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.service.PlayerQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerQuoteServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerQuoteRepository playerQuoteRepository;

    @InjectMocks
    private PlayerQuoteService playerQuoteService;

    private Player player;
    private PlayerQuote quote1;
    private PlayerQuote quote2;

    @BeforeEach
    void setUp() {
        player = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        player.setId(45L);

        quote1 = new PlayerQuote(player, new BigDecimal("2.5000"), "CRD",
                LocalDateTime.of(2026, 10, 1, 12, 0), "Balanced", 1);
        quote1.setId(1001L);

        quote2 = new PlayerQuote(player, new BigDecimal("3.2000"), "CRD",
                LocalDateTime.of(2026, 10, 8, 12, 0), "Balanced", 1);
        quote2.setId(1002L);
    }

    @Test
    @DisplayName("US3 - Consulta paginada devuelve serie histórica ordenada con metadata")
    void getPaginatedQuotes_success() {
        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(playerQuoteRepository.findByPlayerIdOrderByTimestampDesc(eq(45L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(quote2, quote1)));

        PagedResponseDto<PlayerQuoteResponseDto> result = playerQuoteService.getPaginatedQuotes(45L, 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content().get(0).price()).isEqualByComparingTo("3.2000");
        assertThat(result.content().get(1).price()).isEqualByComparingTo("2.5000");
    }

    @Test
    @DisplayName("US3 - Consulta por fecha devuelve la cotización vigente a esa fecha específica")
    void getQuoteByDate_foundEffectiveQuote() {
        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(playerQuoteRepository.findFirstByPlayerIdAndTimestampLessThanEqualOrderByTimestampDesc(eq(45L), any(LocalDateTime.class)))
                .thenReturn(Optional.of(quote1));

        DateEffectiveQuoteResponseDto result = playerQuoteService.getQuoteByDate(45L, LocalDate.of(2026, 10, 5));

        assertThat(result).isNotNull();
        assertThat(result.playerId()).isEqualTo(45L);
        assertThat(result.effectiveDate()).isEqualTo("2026-10-05");
        assertThat(result.price()).isEqualByComparingTo("2.5000");
        assertThat(result.quoteId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("US3 - Consulta a fecha previa al primer registro devuelve valor nominal base de 1.0 crédito")
    void getQuoteByDate_priorToFirstRecord() {
        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(playerQuoteRepository.findFirstByPlayerIdAndTimestampLessThanEqualOrderByTimestampDesc(eq(45L), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        DateEffectiveQuoteResponseDto result = playerQuoteService.getQuoteByDate(45L, LocalDate.of(2026, 1, 1));

        assertThat(result).isNotNull();
        assertThat(result.playerId()).isEqualTo(45L);
        assertThat(result.effectiveDate()).isEqualTo("2026-01-01");
        assertThat(result.price()).isEqualByComparingTo("1.0000");
        assertThat(result.strategyName()).isEqualTo("Valoración Base Inicial");
        assertThat(result.quoteId()).isNull();
    }

    @Test
    @DisplayName("US3 - Consulta de jugador inexistente lanza 404 PLAYER_NOT_FOUND")
    void getQuotes_playerNotFound() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerQuoteService.getPaginatedQuotes(999L, 0, 10))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.PLAYER_NOT_FOUND);
                });
    }
}
