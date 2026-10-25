package com.cbo.players.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_actor_user_id", columnList = "actor_user_id"),
        @Index(name = "idx_transactions_timestamp", columnList = "timestamp")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30, updatable = false)
    private OperationType operationType;

    @Column(name = "player_id", updatable = false)
    private Long playerId;

    @Column(updatable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 14, scale = 4, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 14, scale = 4, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "balance_before", precision = 14, scale = 4, updatable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 14, scale = 4, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "tokens_before", updatable = false)
    private Integer tokensBefore;

    @Column(name = "tokens_after", updatable = false)
    private Integer tokensAfter;

    @Column(name = "strategy_version", nullable = false, updatable = false)
    private Integer strategyVersion;

    public Transaction() {
    }

    public Transaction(Long actorUserId, OperationType operationType, Long playerId, Integer quantity,
                       BigDecimal unitPrice, BigDecimal totalAmount, BigDecimal balanceBefore,
                       BigDecimal balanceAfter, Integer tokensBefore, Integer tokensAfter,
                       Integer strategyVersion) {
        this.timestamp = LocalDateTime.now(ZoneOffset.UTC);
        this.actorUserId = actorUserId;
        this.operationType = operationType;
        this.playerId = playerId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.tokensBefore = tokensBefore;
        this.tokensAfter = tokensAfter;
        this.strategyVersion = strategyVersion != null ? strategyVersion : 1;
    }

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Las transacciones son inmutables y no pueden modificarse.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException("Las transacciones son inmutables y no pueden eliminarse.");
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Integer getTokensBefore() {
        return tokensBefore;
    }

    public Integer getTokensAfter() {
        return tokensAfter;
    }

    public Integer getStrategyVersion() {
        return strategyVersion;
    }
}
