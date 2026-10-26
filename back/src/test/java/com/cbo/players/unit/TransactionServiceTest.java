package com.cbo.players.unit;

import com.cbo.players.dto.response.PagedResponseDto;
import com.cbo.players.dto.response.TransactionResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.Transaction;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.service.TransactionService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User targetUser;
    private Player samplePlayer;

    @BeforeEach
    void setUp() {
        targetUser = new User("user1", "user1@test.com", "hash", UserRole.ROLE_USER);
        targetUser.setId(10L);

        samplePlayer = new Player("Lionel", "Messi", LocalDate.of(1987, 6, 24),
                "Argentina", PlayerPosition.FORWARD, "Inter Miami", "MLS");
        samplePlayer.setId(100L);
    }

    @Test
    @DisplayName("getUserTransactions lanza ApiException 403 si actor no es el dueño ni usuario privilegiado")
    void getUserTransactions_throwsForbiddenWhenUnprivilegedAccessToOtherUser() {
        assertThatThrownBy(() -> transactionService.getUserTransactions(10L, 0, 10, 20L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("getUserTransactions lanza ApiException 404 si el usuario objetivo no existe")
    void getUserTransactions_throwsNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getUserTransactions(10L, 0, 10, 10L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("getUserTransactions mapea transacciones con y sin jugador asociado correctamente")
    void getUserTransactions_mapsTransactionsWithAndWithoutPlayer() throws Exception {
        when(userRepository.findById(10L)).thenReturn(Optional.of(targetUser));

        Transaction txWithPlayerFound = new Transaction(
                10L, OperationType.COMPRA, 100L, 5,
                new BigDecimal("25.0000"), new BigDecimal("125.0000"),
                new BigDecimal("500.0000"), new BigDecimal("375.0000"),
                0, 5, 1
        );

        Transaction txWithPlayerNotFound = new Transaction(
                10L, OperationType.VENTA, 999L, 2,
                new BigDecimal("30.0000"), new BigDecimal("60.0000"),
                new BigDecimal("375.0000"), new BigDecimal("435.0000"),
                5, 3, 1
        );

        Transaction txWithoutPlayer = new Transaction(
                10L, OperationType.RECARGA, null, null,
                null, new BigDecimal("500.0000"),
                BigDecimal.ZERO, new BigDecimal("500.0000"),
                null, null, 1
        );

        java.lang.reflect.Field idField = Transaction.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(txWithPlayerFound, 1L);
        idField.set(txWithPlayerNotFound, 2L);
        idField.set(txWithoutPlayer, 3L);

        when(transactionRepository.findByActorUserIdOrderByTimestampDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(txWithPlayerFound, txWithPlayerNotFound, txWithoutPlayer)));

        when(playerRepository.findById(100L)).thenReturn(Optional.of(samplePlayer));
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        // Acceso permitido para usuario privilegiado aunque target != actor
        PagedResponseDto<TransactionResponseDto> response = transactionService.getUserTransactions(10L, 0, 10, 99L, true);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(3);

        TransactionResponseDto dto1 = response.content().get(0);
        assertThat(dto1.playerName()).isEqualTo("Lionel Messi");
        assertThat(dto1.operationType()).isEqualTo("COMPRA");

        TransactionResponseDto dto2 = response.content().get(1);
        assertThat(dto2.playerName()).isNull();
        assertThat(dto2.operationType()).isEqualTo("VENTA");

        TransactionResponseDto dto3 = response.content().get(2);
        assertThat(dto3.playerId()).isNull();
        assertThat(dto3.playerName()).isNull();
        assertThat(dto3.operationType()).isEqualTo("RECARGA");
    }
}
