package com.cbo.players.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "portfolio_positions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_portfolio_user_player", columnNames = {"user_id", "player_id"})
        },
        indexes = {
                @Index(name = "idx_portfolio_user_id", columnList = "user_id"),
                @Index(name = "idx_portfolio_player_id", columnList = "player_id")
        }
)
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "tokens_quantity", nullable = false)
    private Integer tokensQuantity = 0;

    @Column(name = "average_buy_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageBuyPrice = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PortfolioPosition() {
    }

    public PortfolioPosition(User user, Player player, Integer tokensQuantity, BigDecimal averageBuyPrice) {
        this.user = user;
        this.player = player;
        this.tokensQuantity = tokensQuantity != null ? tokensQuantity : 0;
        this.averageBuyPrice = averageBuyPrice != null ? averageBuyPrice.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    @PreUpdate
    protected void onPersistOrUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        if (this.averageBuyPrice != null) {
            this.averageBuyPrice = this.averageBuyPrice.setScale(4, RoundingMode.HALF_UP);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public Integer getTokensQuantity() {
        return tokensQuantity;
    }

    public void setTokensQuantity(Integer tokensQuantity) {
        this.tokensQuantity = tokensQuantity;
    }

    public BigDecimal getAverageBuyPrice() {
        return averageBuyPrice;
    }

    public void setAverageBuyPrice(BigDecimal averageBuyPrice) {
        this.averageBuyPrice = averageBuyPrice != null ? averageBuyPrice.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
