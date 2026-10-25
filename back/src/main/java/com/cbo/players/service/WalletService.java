package com.cbo.players.service;

import com.cbo.players.dto.response.CreditsTopupResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Transaction;
import com.cbo.players.model.User;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class WalletService {

    private static final BigDecimal MAX_TOPUP_AMOUNT = new BigDecimal("10000.0000");

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ValuationStrategyRepository valuationStrategyRepository;

    public WalletService(UserRepository userRepository,
                         TransactionRepository transactionRepository,
                         ValuationStrategyRepository valuationStrategyRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.valuationStrategyRepository = valuationStrategyRepository;
    }

    @Transactional
    public CreditsTopupResponseDto topupCredits(Long targetUserId, BigDecimal amount, Long actorUserId, boolean isPrivileged) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(MAX_TOPUP_AMOUNT) > 0) {
            throw new ApiException(
                    "El monto a recargar debe ser un valor positivo válido (mayor a 0 y hasta 10000 CRD).",
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_AMOUNT
            );
        }

        if (!targetUserId.equals(actorUserId) && !isPrivileged) {
            throw new ApiException(
                    "Acceso denegado: no posee permisos para recargar créditos a otro usuario.",
                    HttpStatus.FORBIDDEN,
                    ErrorCode.ACCESS_DENIED
            );
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(
                        "Usuario no encontrado con ID: " + targetUserId,
                        HttpStatus.NOT_FOUND,
                        ErrorCode.USER_NOT_FOUND
                ));

        int strategyVersion = valuationStrategyRepository.findByActiveTrue()
                .map(s -> s.getVersion() != null ? s.getVersion() : 1)
                .orElse(1);

        BigDecimal balanceBefore = user.getCreditsBalance() != null
                ? user.getCreditsBalance().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal balanceAfter = balanceBefore.add(amount).setScale(4, RoundingMode.HALF_UP);
        user.setCreditsBalance(balanceAfter);
        userRepository.save(user);

        Transaction transaction = new Transaction(
                actorUserId,
                OperationType.RECARGA,
                null,
                null,
                null,
                amount.setScale(4, RoundingMode.HALF_UP),
                balanceBefore,
                balanceAfter,
                null,
                null,
                strategyVersion
        );
        Transaction savedTx = transactionRepository.save(transaction);

        return new CreditsTopupResponseDto(
                savedTx.getId(),
                user.getId(),
                savedTx.getOperationType().name(),
                amount.setScale(4, RoundingMode.HALF_UP),
                balanceBefore,
                balanceAfter,
                savedTx.getTimestamp()
        );
    }
}
