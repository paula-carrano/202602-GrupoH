package com.cbo.players.service.formula;

import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.model.ValuationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class OffensiveValuationFormula implements ValuationFormula {

    public static final String CODE = "OFFENSIVE";

    @Override
    public String getStrategyCode() {
        return CODE;
    }

    @Override
    public BigDecimal calculate(PlayerStatistics stats, ValuationStrategy strategy) {
        BigDecimal minQuote = resolveMinQuote(strategy);

        if (hasInvalidMinutes(stats)) {
            return minQuote;
        }

        double rawScore = calculateRawScore(stats, strategy);
        if (rawScore < minQuote.doubleValue()) {
            return minQuote;
        }

        return BigDecimal.valueOf(rawScore).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveMinQuote(ValuationStrategy strategy) {
        if (strategy != null && strategy.getMinQuote() != null) {
            return strategy.getMinQuote().setScale(4, RoundingMode.HALF_UP);
        }
        return new BigDecimal("1.0000");
    }

    private boolean hasInvalidMinutes(PlayerStatistics stats) {
        return stats == null || stats.getMinutosJugados() == null || stats.getMinutosJugados() <= 0;
    }

    private double calculateRawScore(PlayerStatistics stats, ValuationStrategy strategy) {
        FormulaWeights weights = resolveWeights(strategy);

        int goals = valueOrZero(stats.getGoals());
        int assists = valueOrZero(stats.getAssists());
        int shots = valueOrZero(stats.getShots());
        int keyPasses = valueOrZero(stats.getKeyPasses());
        int dribbles = valueOrZero(stats.getDribbles());
        int minutes = valueOrZero(stats.getMinutosJugados());
        double rating = valueOrZero(stats.getRating());
        int totalCards = valueOrZero(stats.getTarjetasAmarillas()) + (valueOrZero(stats.getTarjetasRojas()) * 2);

        return (goals * weights.goals())
                + (assists * weights.assists())
                + (shots * weights.shots())
                + (keyPasses * weights.keyPasses())
                + (dribbles * 0.1)
                + (minutes * weights.minutes())
                + (rating * weights.rating())
                + (totalCards * weights.cards());
    }

    private FormulaWeights resolveWeights(ValuationStrategy strategy) {
        if (strategy == null) {
            return new FormulaWeights(1.5, 1.0, 0.005, 0.2, 0.3, 1.2, -0.3);
        }
        return new FormulaWeights(
                strategy.getWeightGoals(),
                strategy.getWeightAssists(),
                strategy.getWeightMinutes(),
                strategy.getWeightShots(),
                strategy.getWeightKeyPasses(),
                strategy.getWeightRating(),
                strategy.getWeightCards()
        );
    }

    private static int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private static double valueOrZero(Double value) {
        return value != null ? value : 0.0;
    }

    private record FormulaWeights(
            double goals,
            double assists,
            double minutes,
            double shots,
            double keyPasses,
            double rating,
            double cards
    ) {}
}
