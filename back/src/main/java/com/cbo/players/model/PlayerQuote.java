package com.cbo.players.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "player_quotes", indexes = {
        @Index(name = "idx_player_quotes_player_id", columnList = "player_id"),
        @Index(name = "idx_player_quotes_timestamp", columnList = "timestamp")
})
public class PlayerQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "CRD";

    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName = "Rendimiento Ofensivo";

    @Column(name = "strategy_version", nullable = false)
    private Integer strategyVersion = 1;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public PlayerQuote() {
    }

    public PlayerQuote(Player player, BigDecimal price, String currency, LocalDateTime timestamp) {
        this(player, price, currency, timestamp, "Rendimiento Ofensivo", 1);
    }

    public PlayerQuote(Player player, BigDecimal price, String currency, LocalDateTime timestamp,
                       String strategyName, Integer strategyVersion) {
        this.player = player;
        this.price = price;
        this.currency = currency != null ? currency : "CRD";
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now(ZoneOffset.UTC);
        this.strategyName = strategyName != null ? strategyName : "Rendimiento Ofensivo";
        this.strategyVersion = strategyVersion != null ? strategyVersion : 1;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public Integer getStrategyVersion() {
        return strategyVersion;
    }

    public void setStrategyVersion(Integer strategyVersion) {
        this.strategyVersion = strategyVersion;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
