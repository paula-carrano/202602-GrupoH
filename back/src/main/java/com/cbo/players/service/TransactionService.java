package com.cbo.players.service;

import com.cbo.players.dto.response.PagedResponseDto;
import com.cbo.players.dto.response.TransactionResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.Transaction;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(UserRepository userRepository,
                              PlayerRepository playerRepository,
                              TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.transactionRepository = transactionRepository;
    }

    public PagedResponseDto<TransactionResponseDto> getUserTransactions(Long targetUserId, int page, int size, Long actorUserId, boolean isPrivileged) {
        if (!targetUserId.equals(actorUserId) && !isPrivileged) {
            throw new ApiException("Acceso denegado a las transacciones de otro usuario", HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
        }

        userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException("Usuario no encontrado con ID: " + targetUserId, HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Transaction> txPage = transactionRepository.findByActorUserIdOrderByTimestampDesc(targetUserId, pageable);

        Page<TransactionResponseDto> dtoPage = txPage.map(tx -> {
            String playerName = null;
            if (tx.getPlayerId() != null) {
                playerName = playerRepository.findById(tx.getPlayerId())
                        .map(p -> p.getFirstName() + " " + p.getLastName())
                        .orElse(null);
            }
            return new TransactionResponseDto(
                    tx.getId(),
                    tx.getTimestamp(),
                    tx.getActorUserId(),
                    tx.getOperationType().name(),
                    tx.getPlayerId(),
                    playerName,
                    tx.getQuantity(),
                    tx.getUnitPrice(),
                    tx.getTotalAmount(),
                    tx.getBalanceBefore(),
                    tx.getBalanceAfter(),
                    tx.getTokensBefore(),
                    tx.getTokensAfter(),
                    tx.getStrategyVersion()
            );
        });

        return PagedResponseDto.from(dtoPage);
    }
}
