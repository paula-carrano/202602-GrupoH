package com.cbo.players.unit;

import com.cbo.players.dto.response.PortfolioPositionDto;
import com.cbo.players.dto.response.PortfolioResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.PortfolioPosition;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PortfolioPositionRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioPositionRepository portfolioPositionRepository;

    @Mock
    private PlayerQuoteRepository playerQuoteRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    private User user;
    private Player player;
    private PortfolioPosition position;
    private PlayerQuote currentQuote;

    @BeforeEach
    void setUp() {
        user = new User("investor_user", "investor@test.com", "hash", UserRole.ROLE_USER);
        user.setId(12L);
        user.setCreditsBalance(new BigDecimal("145.2000"));

        player = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        player.setId(45L);

        // 10 tokens @ 2.0 avg buy price = 20.0 cost basis
        position = new PortfolioPosition(user, player, 10, new BigDecimal("2.0000"));
        position.setId(101L);

        // Current quote: 3.5000 -> current value = 35.0
        currentQuote = new PlayerQuote(player, new BigDecimal("3.5000"), "CRD",
                LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);
    }

    @Test
    @DisplayName("US5 - Cálculo consolidado de métricas de portfolio y PnL nominal/porcentual")
    void getUserPortfolio_success_calculatesPnL() {
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(portfolioPositionRepository.findByUserIdAndTokensQuantityGreaterThan(12L, 0))
                .thenReturn(List.of(position));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(45L))
                .thenReturn(Optional.of(currentQuote));

        PortfolioResponseDto portfolio = portfolioService.getUserPortfolio(12L, 12L, false);

        assertThat(portfolio).isNotNull();
        assertThat(portfolio.userId()).isEqualTo(12L);
        assertThat(portfolio.creditsBalance()).isEqualByComparingTo("145.2000");
        assertThat(portfolio.totalInvestedCost()).isEqualByComparingTo("20.0000");
        assertThat(portfolio.totalCurrentValue()).isEqualByComparingTo("35.0000");
        assertThat(portfolio.totalPnLNominal()).isEqualByComparingTo("15.0000"); // 35 - 20
        assertThat(portfolio.totalPnLPercentage()).isEqualByComparingTo("75.0000"); // 15 / 20 * 100

        assertThat(portfolio.positions()).hasSize(1);
        PortfolioPositionDto posDto = portfolio.positions().get(0);
        assertThat(posDto.tokensQuantity()).isEqualTo(10);
        assertThat(posDto.averageBuyPrice()).isEqualByComparingTo("2.0000");
        assertThat(posDto.currentQuote()).isEqualByComparingTo("3.5000");
        assertThat(posDto.totalCostBasis()).isEqualByComparingTo("20.0000");
        assertThat(posDto.totalCurrentValue()).isEqualByComparingTo("35.0000");
        assertThat(posDto.pnlNominal()).isEqualByComparingTo("15.0000");
        assertThat(posDto.pnlPercentage()).isEqualByComparingTo("75.0000");
    }

    @Test
    @DisplayName("US5 - Acceso denegado al consultar el portfolio de otro usuario sin rol privilegiado")
    void getUserPortfolio_unauthorized() {
        assertThatThrownBy(() -> portfolioService.getUserPortfolio(12L, 99L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("US5 - Error al consultar portfolio de usuario inexistente")
    void getUserPortfolio_notFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getUserPortfolio(99L, 99L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("US5 - Portfolio vacío con saldo nulo retorna totales en cero sin errores")
    void getUserPortfolio_emptyPositionsWithNullBalance() {
        user.setCreditsBalance(null);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(portfolioPositionRepository.findByUserIdAndTokensQuantityGreaterThan(12L, 0)).thenReturn(List.of());

        // Acceso permitido para usuario privilegiado sobre otro target
        PortfolioResponseDto portfolio = portfolioService.getUserPortfolio(12L, 99L, true);

        assertThat(portfolio).isNotNull();
        assertThat(portfolio.creditsBalance()).isEqualByComparingTo("0.0000");
        assertThat(portfolio.totalInvestedCost()).isEqualByComparingTo("0.0000");
        assertThat(portfolio.totalCurrentValue()).isEqualByComparingTo("0.0000");
        assertThat(portfolio.totalPnLNominal()).isEqualByComparingTo("0.0000");
        assertThat(portfolio.totalPnLPercentage()).isEqualByComparingTo("0.0000");
        assertThat(portfolio.positions()).isEmpty();
    }

    @Test
    @DisplayName("US5 - Posición con jugador sin posición definida y sin cotización previa utiliza fallback de 1.0000")
    void getUserPortfolio_playerWithNullPositionAndNoQuote() {
        Player playerWithoutPosition = new Player("Mystery", "Player", LocalDate.of(1995, 5, 5),
                "Unknown", null, "Free Agent", "None");
        playerWithoutPosition.setId(88L);

        PortfolioPosition pos = new PortfolioPosition(user, playerWithoutPosition, 5, BigDecimal.ZERO);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(portfolioPositionRepository.findByUserIdAndTokensQuantityGreaterThan(12L, 0)).thenReturn(List.of(pos));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(88L)).thenReturn(Optional.empty());

        PortfolioResponseDto portfolio = portfolioService.getUserPortfolio(12L, 12L, false);

        assertThat(portfolio.positions()).hasSize(1);
        PortfolioPositionDto dto = portfolio.positions().get(0);
        assertThat(dto.position()).isEmpty();
        assertThat(dto.currentQuote()).isEqualByComparingTo("1.0000");
        assertThat(dto.pnlPercentage()).isEqualByComparingTo("0.0000");
    }
}
