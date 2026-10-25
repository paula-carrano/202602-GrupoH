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
        BigDecimal minQuote = strategy != null && strategy.getMinQuote() != null
                ? strategy.getMinQuote().setScale(4, RoundingMode.HALF_UP)
                : new BigDecimal("1.0000");

        if (stats == null || stats.getMinutosJugados() == null || stats.getMinutosJugados() <= 0) {
            return minQuote;
        }

        int goals = stats.getGoals() != null ? stats.getGoals() : 0;
        int assists = stats.getAssists() != null ? stats.getAssists() : 0;
        int shots = stats.getShots() != null ? stats.getShots() : 0;
        int keyPasses = stats.getKeyPasses() != null ? stats.getKeyPasses() : 0;
        int dribbles = stats.getDribbles() != null ? stats.getDribbles() : 0;
        int minutes = stats.getMinutosJugados() != null ? stats.getMinutosJugados() : 0;
        double rating = stats.getRating() != null ? stats.getRating() : 0.0;
        int yellowCards = stats.getTarjetasAmarillas() != null ? stats.getTarjetasAmarillas() : 0;
        int redCards = stats.getTarjetasRojas() != null ? stats.getTarjetasRojas() : 0;
        int totalCards = yellowCards + (redCards * 2);

        double weightGoals = strategy != null ? strategy.getWeightGoals() : 1.5;
        double weightAssists = strategy != null ? strategy.getWeightAssists() : 1.0;
        double weightMinutes = strategy != null ? strategy.getWeightMinutes() : 0.005;
        double weightShots = strategy != null ? strategy.getWeightShots() : 0.2;
        double weightKeyPasses = strategy != null ? strategy.getWeightKeyPasses() : 0.3;
        double weightRating = strategy != null ? strategy.getWeightRating() : 1.2;
        double weightCards = strategy != null ? strategy.getWeightCards() : -0.3;

        double rawScore = (goals * weightGoals)
                + (assists * weightAssists)
                + (shots * weightShots)
                + (keyPasses * weightKeyPasses)
                + (dribbles * 0.1)
                + (minutes * weightMinutes)
                + (rating * weightRating)
                + (totalCards * weightCards);

        if (rawScore < minQuote.doubleValue()) {
            return minQuote;
        }

        return BigDecimal.valueOf(rawScore).setScale(4, RoundingMode.HALF_UP);
    }
}
