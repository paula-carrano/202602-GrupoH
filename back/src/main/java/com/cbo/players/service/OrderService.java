package com.cbo.players.service;

import com.cbo.players.model.records.TransactionData;
import com.cbo.players.dto.response.OrderResponseDto;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class OrderService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository playerQuoteRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final TransactionRepository transactionRepository;

    public OrderService(UserRepository userRepository,
                        PlayerRepository playerRepository,
                        PlayerQuoteRepository playerQuoteRepository,
                        PortfolioPositionRepository portfolioPositionRepository,
                        TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.playerQuoteRepository = playerQuoteRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponseDto executeBuyOrder(Long buyerUserId, Long playerId, Integer quantity) {
        validateOrderParameters(quantity);

        Player player = getActivePlayerOrThrow(playerId);
        User buyer = getUserOrThrow(buyerUserId);
        User superuser = getSuperuserOrThrow();

        PlayerQuote latestQuote = playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(playerId).orElse(null);
        BigDecimal unitPrice = latestQuote != null ? latestQuote.getPrice() : BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        int strategyVersion = latestQuote != null && latestQuote.getStrategyVersion() != null ? latestQuote.getStrategyVersion() : 1;

        BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal buyerBalance = buyer.getCreditsBalance() != null
                ? buyer.getCreditsBalance().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        if (buyerBalance.compareTo(totalCost) < 0) {
            throw new ApiException(
                    String.format("Saldo insuficiente para realizar la compra. Saldo disponible: %.4f CRD, costo total: %.4f CRD.", buyerBalance, totalCost),
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INSUFFICIENT_FUNDS
            );
        }

        PortfolioPosition superuserPosition = getOrInitSuperuserPositionWithLock(superuser, player);
        if (superuserPosition.getTokensQuantity() < quantity) {
            throw new ApiException(
                    String.format("Disponibilidad insuficiente de tokens. Solo quedan %d tokens disponibles en el mercado para este jugador.", superuserPosition.getTokensQuantity()),
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INSUFFICIENT_TOKENS_AVAILABLE
            );
        }

        PortfolioPosition buyerPosition = portfolioPositionRepository.findByUserIdAndPlayerId(buyer.getId(), playerId)
                .orElseGet(() -> new PortfolioPosition(buyer, player, 0, BigDecimal.ZERO));

        int tokensBefore = buyerPosition.getTokensQuantity();
        int tokensAfter = tokensBefore + quantity;
        BigDecimal balanceBefore = buyerBalance;
        BigDecimal balanceAfter = balanceBefore.subtract(totalCost).setScale(4, RoundingMode.HALF_UP);

        BigDecimal avgBuyPrice;
        if (tokensBefore == 0) {
            avgBuyPrice = unitPrice.setScale(4, RoundingMode.HALF_UP);
        } else {
            BigDecimal prevTotal = buyerPosition.getAverageBuyPrice().multiply(BigDecimal.valueOf(tokensBefore));
            BigDecimal newTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            avgBuyPrice = prevTotal.add(newTotal).divide(BigDecimal.valueOf(tokensAfter), 4, RoundingMode.HALF_UP);
        }

        // Apply changes
        buyer.setCreditsBalance(balanceAfter);
        userRepository.save(buyer);

        superuserPosition.setTokensQuantity(superuserPosition.getTokensQuantity() - quantity);
        portfolioPositionRepository.save(superuserPosition);

        buyerPosition.setTokensQuantity(tokensAfter);
        buyerPosition.setAverageBuyPrice(avgBuyPrice);
        portfolioPositionRepository.save(buyerPosition);

        Transaction tx = new Transaction(new TransactionData(
                    buyer.getId(),
                    OperationType.COMPRA,
                    player.getId(),
                    quantity,
                    unitPrice,
                    totalCost,
                    balanceBefore,
                    balanceAfter,
                    tokensBefore,
                    tokensAfter,
                    strategyVersion
            ));
        Transaction savedTx = transactionRepository.save(tx);

        String playerName = player.getFirstName() + " " + player.getLastName();
        return new OrderResponseDto(
                savedTx.getId(),
                "COMPRA",
                player.getId(),
                playerName,
                quantity,
                unitPrice,
                totalCost,
                balanceBefore,
                balanceAfter,
                tokensBefore,
                tokensAfter,
                avgBuyPrice,
                savedTx.getTimestamp()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponseDto executeSellOrder(Long sellerUserId, Long playerId, Integer quantity) {
        validateOrderParameters(quantity);

        Player player = getActivePlayerOrThrow(playerId);
        User seller = getUserOrThrow(sellerUserId);
        User superuser = getSuperuserOrThrow();

        PortfolioPosition sellerPosition = portfolioPositionRepository.findByUserIdAndPlayerId(seller.getId(), playerId)
                .orElse(null);
        int sellerTokens = sellerPosition != null ? sellerPosition.getTokensQuantity() : 0;

        if (sellerTokens < quantity) {
            throw new ApiException(
                    String.format("Tenencia insuficiente. El usuario posee %d tokens del jugador e intentó vender %d.", sellerTokens, quantity),
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INSUFFICIENT_TOKEN_BALANCE
            );
        }

        PortfolioPosition superuserPosition = getOrInitSuperuserPositionWithLock(superuser, player);

        PlayerQuote latestQuote = playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(playerId).orElse(null);
        BigDecimal unitPrice = latestQuote != null ? latestQuote.getPrice() : BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        int strategyVersion = latestQuote != null && latestQuote.getStrategyVersion() != null ? latestQuote.getStrategyVersion() : 1;

        BigDecimal proceeds = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal balanceBefore = seller.getCreditsBalance() != null
                ? seller.getCreditsBalance().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal balanceAfter = balanceBefore.add(proceeds).setScale(4, RoundingMode.HALF_UP);

        int tokensBefore = sellerTokens;
        int tokensAfter = sellerTokens - quantity;

        // Apply changes
        seller.setCreditsBalance(balanceAfter);
        userRepository.save(seller);

        sellerPosition.setTokensQuantity(tokensAfter);
        portfolioPositionRepository.save(sellerPosition);

        superuserPosition.setTokensQuantity(superuserPosition.getTokensQuantity() + quantity);
        portfolioPositionRepository.save(superuserPosition);

        Transaction tx = new Transaction(new TransactionData(
                    seller.getId(),
                    OperationType.VENTA,
                    player.getId(),
                    quantity,
                    unitPrice,
                    proceeds,
                    balanceBefore,
                    balanceAfter,
                    tokensBefore,
                    tokensAfter,
                    strategyVersion
            ));
        Transaction savedTx = transactionRepository.save(tx);

        String playerName = player.getFirstName() + " " + player.getLastName();
        return new OrderResponseDto(
                savedTx.getId(),
                "VENTA",
                player.getId(),
                playerName,
                quantity,
                unitPrice,
                proceeds,
                balanceBefore,
                balanceAfter,
                tokensBefore,
                tokensAfter,
                sellerPosition.getAverageBuyPrice(),
                savedTx.getTimestamp()
        );
    }

    private void validateOrderParameters(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new ApiException("La cantidad de tokens debe ser mayor a 0", HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT);
        }
    }

    private Player getActivePlayerOrThrow(Long playerId) {
        return playerRepository.findById(playerId)
                .filter(Player::isActive)
                .orElseThrow(() -> new ApiException("Jugador no encontrado con ID: " + playerId, HttpStatus.NOT_FOUND, ErrorCode.PLAYER_NOT_FOUND));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("Usuario no encontrado con ID: " + userId, HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND));
    }

    private User getSuperuserOrThrow() {
        return userRepository.findFirstByRole(UserRole.ROLE_SUPERUSER)
                .or(() -> userRepository.findByUsername("superuser"))
                .or(() -> userRepository.findFirstByRole(UserRole.ROLE_ADMIN))
                .orElseThrow(() -> new ApiException("Superusuario del sistema no configurado", HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private PortfolioPosition getOrInitSuperuserPositionWithLock(User superuser, Player player) {
        PortfolioPosition pos = portfolioPositionRepository.findByUserIdAndPlayerIdWithLock(superuser.getId(), player.getId())
                .orElse(null);

        if (pos == null) {
            pos = portfolioPositionRepository.findByUserIdAndPlayerId(superuser.getId(), player.getId())
                    .orElseGet(() -> {
                        PortfolioPosition initial = new PortfolioPosition(superuser, player, 100, BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
                        return portfolioPositionRepository.saveAndFlush(initial);
                    });
            pos = portfolioPositionRepository.findByUserIdAndPlayerIdWithLock(superuser.getId(), player.getId())
                    .orElse(pos);
        }
        return pos;
    }
}
