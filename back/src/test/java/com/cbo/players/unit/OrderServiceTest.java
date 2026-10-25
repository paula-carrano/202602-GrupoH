package com.cbo.players.unit;

import com.cbo.players.dto.response.OrderResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import com.cbo.players.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerQuoteRepository playerQuoteRepository;

    @Mock
    private PortfolioPositionRepository portfolioPositionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private OrderService orderService;

    private User buyer;
    private User superuser;
    private Player player;
    private PlayerQuote quote;
    private PortfolioPosition superuserPosition;

    @BeforeEach
    void setUp() {
        buyer = new User("buyer_test", "buyer@test.com", "hash", UserRole.ROLE_USER);
        buyer.setId(10L);
        buyer.setCreditsBalance(new BigDecimal("200.0000"));

        superuser = new User("superuser_test", "superuser@test.com", "hash", UserRole.ROLE_SUPERUSER);
        superuser.setId(1L);

        player = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        player.setId(45L);
        player.setActive(true);

        quote = new PlayerQuote(player, new BigDecimal("27.4000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);
        quote.setId(101L);

        superuserPosition = new PortfolioPosition(superuser, player, 100, BigDecimal.ONE);
        superuserPosition.setId(1L);
    }

    @Test
    @DisplayName("US2 - Compra exitosa deduce saldo, transfiere tokens y calcula precio promedio ponderado")
    void executeBuyOrder_success() {
        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(userRepository.findFirstByRole(UserRole.ROLE_SUPERUSER)).thenReturn(Optional.of(superuser));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(45L)).thenReturn(Optional.of(quote));
        when(portfolioPositionRepository.findByUserIdAndPlayerIdWithLock(1L, 45L)).thenReturn(Optional.of(superuserPosition));
        when(portfolioPositionRepository.findByUserIdAndPlayerId(10L, 45L)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            return t;
        });

        OrderResponseDto response = orderService.executeBuyOrder(10L, 45L, 4);

        assertThat(response).isNotNull();
        assertThat(response.orderType()).isEqualTo("COMPRA");
        assertThat(response.playerId()).isEqualTo(45L);
        assertThat(response.quantity()).isEqualTo(4);
        assertThat(response.unitPrice()).isEqualByComparingTo("27.4000");
        assertThat(response.totalAmount()).isEqualByComparingTo("109.6000"); // 4 * 27.40
        assertThat(response.balanceBefore()).isEqualByComparingTo("200.0000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("90.4000"); // 200 - 109.60
        assertThat(response.tokensBefore()).isEqualTo(0);
        assertThat(response.tokensAfter()).isEqualTo(4);
        assertThat(response.averageBuyPrice()).isEqualByComparingTo("27.4000");

        assertThat(superuserPosition.getTokensQuantity()).isEqualTo(96);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("US2 - Compra rechazada si el saldo es insuficiente")
    void executeBuyOrder_insufficientFunds() {
        buyer.setCreditsBalance(new BigDecimal("10.0000"));

        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(userRepository.findFirstByRole(UserRole.ROLE_SUPERUSER)).thenReturn(Optional.of(superuser));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(45L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> orderService.executeBuyOrder(10L, 45L, 4))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Saldo insuficiente para realizar la compra")
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
                });

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("US2 - Compra rechazada si los tokens disponibles en superusuario son insuficientes")
    void executeBuyOrder_insufficientTokensAvailable() {
        superuserPosition.setTokensQuantity(2);

        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(userRepository.findFirstByRole(UserRole.ROLE_SUPERUSER)).thenReturn(Optional.of(superuser));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(45L)).thenReturn(Optional.of(quote));
        when(portfolioPositionRepository.findByUserIdAndPlayerIdWithLock(1L, 45L)).thenReturn(Optional.of(superuserPosition));

        assertThatThrownBy(() -> orderService.executeBuyOrder(10L, 45L, 5))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Disponibilidad insuficiente de tokens")
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_TOKENS_AVAILABLE);
                });

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("US2 - Compra subsiguiente calcula correctamente el precio promedio ponderado")
    void executeBuyOrder_subsequentBuyWeightedAverage() {
        PortfolioPosition buyerExistingPosition = new PortfolioPosition(buyer, player, 2, new BigDecimal("10.0000"));

        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(userRepository.findFirstByRole(UserRole.ROLE_SUPERUSER)).thenReturn(Optional.of(superuser));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(45L)).thenReturn(Optional.of(quote)); // 27.40
        when(portfolioPositionRepository.findByUserIdAndPlayerIdWithLock(1L, 45L)).thenReturn(Optional.of(superuserPosition));
        when(portfolioPositionRepository.findByUserIdAndPlayerId(10L, 45L)).thenReturn(Optional.of(buyerExistingPosition));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Prev: 2 tokens @ 10.00 = 20.00
        // New: 2 tokens @ 27.40 = 54.80
        // Total cost: 74.80 / 4 = 18.7000
        OrderResponseDto response = orderService.executeBuyOrder(10L, 45L, 2);

        assertThat(response.tokensBefore()).isEqualTo(2);
        assertThat(response.tokensAfter()).isEqualTo(4);
        assertThat(response.averageBuyPrice()).isEqualByComparingTo("18.7000");
    }

    @Test
    @DisplayName("US2 - Venta exitosa acredita fondos, devuelve tokens al superusuario y mantiene precio promedio")
    void executeSellOrder_success() {
        PortfolioPosition sellerPosition = new PortfolioPosition(buyer, player, 5, new BigDecimal("20.0000"));

        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(userRepository.findFirstByRole(UserRole.ROLE_SUPERUSER)).thenReturn(Optional.of(superuser));
        when(playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(45L)).thenReturn(Optional.of(quote)); // 27.40
        when(portfolioPositionRepository.findByUserIdAndPlayerId(10L, 45L)).thenReturn(Optional.of(sellerPosition));
        when(portfolioPositionRepository.findByUserIdAndPlayerIdWithLock(1L, 45L)).thenReturn(Optional.of(superuserPosition));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderResponseDto response = orderService.executeSellOrder(10L, 45L, 2);

        assertThat(response.orderType()).isEqualTo("VENTA");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("54.8000"); // 2 * 27.40
        assertThat(response.balanceBefore()).isEqualByComparingTo("200.0000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("254.8000");
        assertThat(response.tokensBefore()).isEqualTo(5);
        assertThat(response.tokensAfter()).isEqualTo(3);
        assertThat(response.averageBuyPrice()).isEqualByComparingTo("20.0000");

        assertThat(superuserPosition.getTokensQuantity()).isEqualTo(102);
    }

    @Test
    @DisplayName("US2 - Venta rechazada si la tenencia de tokens es insuficiente")
    void executeSellOrder_insufficientTokens() {
        PortfolioPosition sellerPosition = new PortfolioPosition(buyer, player, 2, new BigDecimal("20.0000"));

        when(playerRepository.findById(45L)).thenReturn(Optional.of(player));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(userRepository.findFirstByRole(UserRole.ROLE_SUPERUSER)).thenReturn(Optional.of(superuser));
        when(portfolioPositionRepository.findByUserIdAndPlayerId(10L, 45L)).thenReturn(Optional.of(sellerPosition));

        assertThatThrownBy(() -> orderService.executeSellOrder(10L, 45L, 3))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tenencia insuficiente")
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_TOKEN_BALANCE);
                });
    }

    @Test
    @DisplayName("US2 - Orden rechazada si la cantidad de tokens es menor o igual a cero")
    void executeOrder_invalidQuantity() {
        assertThatThrownBy(() -> orderService.executeBuyOrder(10L, 45L, 0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("La cantidad de tokens debe ser mayor a 0")
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT);
                });
    }
}
