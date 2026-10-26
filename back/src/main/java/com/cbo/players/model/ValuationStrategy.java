package com.cbo.players.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "valuation_strategies")
public class ValuationStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Integer version = 1;

    public static final String DEFAULT_MIN_QUOTE_STRING = "1.0000";
    public static final BigDecimal DEFAULT_MIN_QUOTE = new BigDecimal(DEFAULT_MIN_QUOTE_STRING);

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "min_quote", nullable = false, precision = 14, scale = 4)
    private BigDecimal minQuote = DEFAULT_MIN_QUOTE;

    @Column(name = "weight_goals", nullable = false)
    private Double weightGoals = 1.0;

    @Column(name = "weight_assists", nullable = false)
    private Double weightAssists = 0.8;

    @Column(name = "weight_minutes", nullable = false)
    private Double weightMinutes = 0.01;

    @Column(name = "weight_shots", nullable = false)
    private Double weightShots = 0.1;

    @Column(name = "weight_key_passes", nullable = false)
    private Double weightKeyPasses = 0.2;

    @Column(name = "weight_rating", nullable = false)
    private Double weightRating = 1.5;

    @Column(name = "weight_cards", nullable = false)
    private Double weightCards = -0.5;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ValuationStrategy() {
    }

    public ValuationStrategy(String code, String name, String description, Integer version, boolean active,
                             BigDecimal minQuote, Double weightGoals, Double weightAssists, Double weightMinutes,
                             Double weightShots, Double weightKeyPasses, Double weightRating, Double weightCards) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.version = version != null ? version : 1;
        this.active = active;
        this.minQuote = minQuote != null ? minQuote.setScale(4, RoundingMode.HALF_UP) : DEFAULT_MIN_QUOTE;
        this.weightGoals = weightGoals != null ? weightGoals : 1.0;
        this.weightAssists = weightAssists != null ? weightAssists : 0.8;
        this.weightMinutes = weightMinutes != null ? weightMinutes : 0.01;
        this.weightShots = weightShots != null ? weightShots : 0.1;
        this.weightKeyPasses = weightKeyPasses != null ? weightKeyPasses : 0.2;
        this.weightRating = weightRating != null ? weightRating : 1.5;
        this.weightCards = weightCards != null ? weightCards : -0.5;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    protected void onCreate() {
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (this.minQuote == null) {
            this.minQuote = DEFAULT_MIN_QUOTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Map<String, Double> getWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("goals", this.weightGoals);
        weights.put("assists", this.weightAssists);
        weights.put("minutes", this.weightMinutes);
        weights.put("shots", this.weightShots);
        weights.put("keyPasses", this.weightKeyPasses);
        weights.put("rating", this.weightRating);
        weights.put("cards", this.weightCards);
        return weights;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BigDecimal getMinQuote() {
        return minQuote;
    }

    public void setMinQuote(BigDecimal minQuote) {
        this.minQuote = minQuote != null ? minQuote.setScale(4, RoundingMode.HALF_UP) : DEFAULT_MIN_QUOTE;
    }

    public Double getWeightGoals() {
        return weightGoals;
    }

    public void setWeightGoals(Double weightGoals) {
        this.weightGoals = weightGoals;
    }

    public Double getWeightAssists() {
        return weightAssists;
    }

    public void setWeightAssists(Double weightAssists) {
        this.weightAssists = weightAssists;
    }

    public Double getWeightMinutes() {
        return weightMinutes;
    }

    public void setWeightMinutes(Double weightMinutes) {
        this.weightMinutes = weightMinutes;
    }

    public Double getWeightShots() {
        return weightShots;
    }

    public void setWeightShots(Double weightShots) {
        this.weightShots = weightShots;
    }

    public Double getWeightKeyPasses() {
        return weightKeyPasses;
    }

    public void setWeightKeyPasses(Double weightKeyPasses) {
        this.weightKeyPasses = weightKeyPasses;
    }

    public Double getWeightRating() {
        return weightRating;
    }

    public void setWeightRating(Double weightRating) {
        this.weightRating = weightRating;
    }

    public Double getWeightCards() {
        return weightCards;
    }

    public void setWeightCards(Double weightCards) {
        this.weightCards = weightCards;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
