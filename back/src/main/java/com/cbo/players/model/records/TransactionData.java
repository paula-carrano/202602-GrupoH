package com.cbo.players.model.records;

import com.cbo.players.model.OperationType;
import java.math.BigDecimal;

public record TransactionData(
        Long actorUserId,
        OperationType operationType,
        Long playerId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        Integer tokensBefore,
        Integer tokensAfter,
        Integer strategyVersion
) {}
