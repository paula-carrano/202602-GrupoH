package com.cbo.players.unit;

import com.cbo.players.dto.response.CreditsTopupResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Transaction;
import com.cbo.players.model.User;
import com.cbo.players.model.UserRole;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import com.cbo.players.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ValuationStrategyRepository valuationStrategyRepository;

    @InjectMocks
    private WalletService walletService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("buyer", "buyer@test.com", "hash", UserRole.ROLE_USER);
        sampleUser.setId(10L);
        sampleUser.setCreditsBalance(new BigDecimal("100.0000"));
    }

    @Test
    @DisplayName("topupCredits lanza ApiException 400 si el monto es nulo")
    void topupCredits_throwsBadRequestWhenAmountIsNull() {
        assertThatThrownBy(() -> walletService.topupCredits(10L, null, 10L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0000", "-5.0000", "10000.0001", "20000.0000"})
    @DisplayName("topupCredits lanza ApiException 400 si el monto es cero, negativo o excede 10000 CRD")
    void topupCredits_throwsBadRequestWhenAmountIsOutOfRange(String amountStr) {
        BigDecimal amount = new BigDecimal(amountStr);

        assertThatThrownBy(() -> walletService.topupCredits(10L, amount, 10L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT);
                });
    }

    @Test
    @DisplayName("topupCredits lanza ApiException 403 si un usuario sin privilegios intenta recargar a otro")
    void topupCredits_throwsForbiddenWhenUnprivilegedAccessToOtherUser() {
        assertThatThrownBy(() -> walletService.topupCredits(10L, new BigDecimal("50.0000"), 20L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("topupCredits lanza ApiException 404 si el usuario objetivo no existe")
    void topupCredits_throwsNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.topupCredits(99L, new BigDecimal("50.0000"), 99L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("topupCredits incrementa el balance, persiste el usuario y registra la transacción")
    void topupCredits_successIncrementsBalanceAndSavesTransaction() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ValuationStrategy strategy = mock(ValuationStrategy.class);
        when(strategy.getVersion()).thenReturn(2);
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));

        CreditsTopupResponseDto response = walletService.topupCredits(10L, new BigDecimal("250.0000"), 10L, false);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.amountCredited()).isEqualByComparingTo("250.0000");
        assertThat(response.balanceBefore()).isEqualByComparingTo("100.0000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("350.0000");

        verify(userRepository).save(sampleUser);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.getActorUserId()).isEqualTo(10L);
        assertThat(tx.getOperationType()).isEqualTo(OperationType.RECARGA);
        assertThat(tx.getStrategyVersion()).isEqualTo(2);
        assertThat(tx.getTotalAmount()).isEqualByComparingTo("250.0000");
    }

    @Test
    @DisplayName("topupCredits maneja usuario con saldo inicial nulo y estrategia activa sin versión o inexistente")
    void topupCredits_handlesNullInitialBalanceAndNullStrategyVersion() {
        sampleUser.setCreditsBalance(null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ValuationStrategy strategyWithoutVersion = mock(ValuationStrategy.class);
        when(strategyWithoutVersion.getVersion()).thenReturn(null);
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategyWithoutVersion));

        // Permitido para usuario privilegiado sobre targetUserId diferente
        CreditsTopupResponseDto response = walletService.topupCredits(10L, new BigDecimal("100.0000"), 99L, true);

        assertThat(response.balanceBefore()).isEqualByComparingTo("0.0000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("100.0000");

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStrategyVersion()).isEqualTo(1);
    }
}
