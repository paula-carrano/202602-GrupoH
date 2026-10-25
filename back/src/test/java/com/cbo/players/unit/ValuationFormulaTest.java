package com.cbo.players.unit;

import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.service.formula.BalancedValuationFormula;
import com.cbo.players.service.formula.OffensiveValuationFormula;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ValuationFormulaTest {

    private OffensiveValuationFormula offensiveFormula;
    private BalancedValuationFormula balancedFormula;
    private ValuationStrategy offensiveStrategy;
    private ValuationStrategy balancedStrategy;
    private Player samplePlayer;

    @BeforeEach
    void setUp() {
        offensiveFormula = new OffensiveValuationFormula();
        balancedFormula = new BalancedValuationFormula();

        offensiveStrategy = new ValuationStrategy(
                "OFFENSIVE",
                "Rendimiento Ofensivo",
                "Estrategia ofensiva",
                1,
                true,
                new BigDecimal("1.0000"),
                1.5,
                1.0,
                0.005,
                0.2,
                0.3,
                1.2,
                -0.3
        );

        balancedStrategy = new ValuationStrategy(
                "BALANCED",
                "Rendimiento Global Equilibrado",
                "Estrategia balanceada",
                1,
                false,
                new BigDecimal("1.0000"),
                1.0,
                0.8,
                0.01,
                0.1,
                0.2,
                1.5,
                -0.5
        );

        samplePlayer = new Player("Lionel", "Messi", LocalDate.of(1987, 6, 24), "Argentina",
                PlayerPosition.FORWARD, "Inter Miami", "MLS");
    }

    @Test
    @DisplayName("OffensiveValuationFormula debe reportar el código OFFENSIVE")
    void offensiveFormulaReportsCorrectCode() {
        assertThat(offensiveFormula.getStrategyCode()).isEqualTo("OFFENSIVE");
    }

    @Test
    @DisplayName("BalancedValuationFormula debe reportar el código BALANCED")
    void balancedFormulaReportsCorrectCode() {
        assertThat(balancedFormula.getStrategyCode()).isEqualTo("BALANCED");
    }

    @Test
    @DisplayName("Cálculo ofensivo con estadísticas válidas y escala de 4 decimales")
    void offensiveFormulaCalculatesExpectedQuote() {
        PlayerStatistics stats = new PlayerStatistics(
                samplePlayer, 100L,
                12, // goals
                8,  // assists
                30, // shots
                20, // key passes
                15, // dribbles
                5,  // tackles
                8.10, // rating
                900,  // minutes
                1,    // yellow cards
                0     // red cards
        );

        BigDecimal quote = offensiveFormula.calculate(stats, offensiveStrategy);

        // Expected calculation:
        // goals: 12 * 1.5 = 18.0
        // assists: 8 * 1.0 = 8.0
        // shots: 30 * 0.2 = 6.0
        // keyPasses: 20 * 0.3 = 6.0
        // dribbles: 15 * 0.1 = 1.5
        // minutes: 900 * 0.005 = 4.5
        // rating: 8.10 * 1.2 = 9.72
        // cards: 1 * (-0.3) = -0.3
        // sum = 18.0 + 8.0 + 6.0 + 6.0 + 1.5 + 4.5 + 9.72 - 0.3 = 53.4200
        assertThat(quote).isNotNull();
        assertThat(quote.scale()).isEqualTo(4);
        assertThat(quote).isEqualByComparingTo(new BigDecimal("53.4200"));
    }

    @Test
    @DisplayName("Cálculo balanceado con estadísticas válidas considerando tackles y minutos")
    void balancedFormulaCalculatesExpectedQuote() {
        PlayerStatistics stats = new PlayerStatistics(
                samplePlayer, 100L,
                5,   // goals
                4,   // assists
                15,  // shots
                10,  // key passes
                8,   // dribbles
                20,  // tackles
                7.50, // rating
                1200, // minutes
                2,    // yellow cards
                0     // red cards
        );

        BigDecimal quote = balancedFormula.calculate(stats, balancedStrategy);

        // Expected calculation:
        // goals: 5 * 1.0 = 5.0
        // assists: 4 * 0.8 = 3.2
        // shots: 15 * 0.1 = 1.5
        // keyPasses: 10 * 0.2 = 2.0
        // dribbles: 8 * 0.05 = 0.4
        // tackles: 20 * 0.05 = 1.0
        // minutes: 1200 * 0.01 = 12.0
        // rating: 7.50 * 1.5 = 11.25
        // cards: 2 * (-0.5) = -1.0
        // sum = 5.0 + 3.2 + 1.5 + 2.0 + 0.4 + 1.0 + 12.0 + 11.25 - 1.0 = 35.3500
        assertThat(quote).isNotNull();
        assertThat(quote.scale()).isEqualTo(4);
        assertThat(quote).isEqualByComparingTo(new BigDecimal("35.3500"));
    }

    @Test
    @DisplayName("Jugador con estadísticas nulas o sin minutos retorna cotización mínima de 1.0000")
    void formulasReturnMinQuoteWhenStatsAreNullOrZeroMinutes() {
        BigDecimal minQuote = new BigDecimal("1.0000");

        assertThat(offensiveFormula.calculate(null, offensiveStrategy)).isEqualByComparingTo(minQuote);
        assertThat(balancedFormula.calculate(null, balancedStrategy)).isEqualByComparingTo(minQuote);

        PlayerStatistics zeroMinutes = new PlayerStatistics(
                samplePlayer, 100L, 0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0
        );

        assertThat(offensiveFormula.calculate(zeroMinutes, offensiveStrategy)).isEqualByComparingTo(minQuote);
        assertThat(balancedFormula.calculate(zeroMinutes, balancedStrategy)).isEqualByComparingTo(minQuote);
    }

    @Test
    @DisplayName("Puntaje resultante menor al mínimo garantizado retorna minQuote (piso de seguridad)")
    void formulasFloorAtMinQuoteWhenScoreIsNegativeOrBelowFloor() {
        // Player with many cards causing negative raw score
        PlayerStatistics negativeStats = new PlayerStatistics(
                samplePlayer, 100L,
                0, 0, 0, 0, 0, 0, 1.0, 10, 5, 3
        );

        BigDecimal quoteOffensive = offensiveFormula.calculate(negativeStats, offensiveStrategy);
        BigDecimal quoteBalanced = balancedFormula.calculate(negativeStats, balancedStrategy);

        assertThat(quoteOffensive).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(quoteBalanced).isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @Test
    @DisplayName("Modificación dinámica de pesos en la estrategia altera el resultado del cálculo")
    void dynamicWeightsChangeCalculatedQuote() {
        PlayerStatistics stats = new PlayerStatistics(
                samplePlayer, 100L, 5, 0, 10, 0, 0, 0, 6.0, 500, 0, 0
        );

        BigDecimal quoteInitial = offensiveFormula.calculate(stats, offensiveStrategy);

        // Aumentar peso de goles
        offensiveStrategy.setWeightGoals(3.0);
        BigDecimal quoteModified = offensiveFormula.calculate(stats, offensiveStrategy);

        assertThat(quoteModified).isGreaterThan(quoteInitial);
    }
}
