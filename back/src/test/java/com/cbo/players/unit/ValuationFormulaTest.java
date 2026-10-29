package com.cbo.players.unit;

import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.adapter.dto.PlayerStatsDto;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.service.formula.BalancedValuationFormula;
import com.cbo.players.service.formula.OffensiveValuationFormula;
import com.cbo.players.service.formula.ValuationFormula;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                new BigDecimal("1.0000"), new ValuationWeights(
                    1.5,
                    1.0,
                    0.005,
                    0.2,
                    0.3,
                    1.2,
                    -0.3
            ));

        balancedStrategy = new ValuationStrategy(
                "BALANCED",
                "Rendimiento Global Equilibrado",
                "Estrategia balanceada",
                1,
                false,
                new BigDecimal("1.0000"), new ValuationWeights(
                    1.0,
                    0.8,
                    0.01,
                    0.1,
                    0.2,
                    1.5,
                    -0.5
            ));

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
                samplePlayer, 100L, new PlayerStatsDto(
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
            ));

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
                samplePlayer, 100L, new PlayerStatsDto(
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
            ));

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
                samplePlayer, 100L, new PlayerStatsDto( 0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0
            ));

        assertThat(offensiveFormula.calculate(zeroMinutes, offensiveStrategy)).isEqualByComparingTo(minQuote);
        assertThat(balancedFormula.calculate(zeroMinutes, balancedStrategy)).isEqualByComparingTo(minQuote);
    }

    @Test
    @DisplayName("Puntaje resultante menor al mínimo garantizado retorna minQuote (piso de seguridad)")
    void formulasFloorAtMinQuoteWhenScoreIsNegativeOrBelowFloor() {
        // Player with many cards causing negative raw score
        PlayerStatistics negativeStats = new PlayerStatistics(
                samplePlayer, 100L, new PlayerStatsDto(
                    0, 0, 0, 0, 0, 0, 1.0, 10, 5, 3
            ));

        BigDecimal quoteOffensive = offensiveFormula.calculate(negativeStats, offensiveStrategy);
        BigDecimal quoteBalanced = balancedFormula.calculate(negativeStats, balancedStrategy);

        assertThat(quoteOffensive).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(quoteBalanced).isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @Test
    @DisplayName("Modificación dinámica de pesos en la estrategia altera el resultado del cálculo")
    void dynamicWeightsChangeCalculatedQuote() {
        PlayerStatistics stats = new PlayerStatistics(
                samplePlayer, 100L, new PlayerStatsDto( 5, 0, 10, 0, 0, 0, 6.0, 500, 0, 0
            ));

        BigDecimal quoteInitial = offensiveFormula.calculate(stats, offensiveStrategy);

        // Aumentar peso de goles
        offensiveStrategy.setWeightGoals(3.0);
        BigDecimal quoteModified = offensiveFormula.calculate(stats, offensiveStrategy);

        assertThat(quoteModified).isGreaterThan(quoteInitial);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFormulas")
    @DisplayName("Fórmulas retornan minQuote cuando minutos son nulos, cero o negativos")
    void formulasReturnMinQuoteOnInvalidMinutes(String formulaName, ValuationFormula formula) {
        ValuationStrategy customMinStrategy = new ValuationStrategy(
                "TEST", "Test Strategy", "Desc", 1, true, new BigDecimal("2.5000"), new ValuationWeights(
                    1.0, 1.0, 0.01, 0.1, 0.1, 1.0, -0.5
            ));

        // Stats con minutos nulos
        PlayerStatistics statsNullMinutes = mock(PlayerStatistics.class);
        when(statsNullMinutes.getMinutosJugados()).thenReturn(null);
        assertThat(formula.calculate(statsNullMinutes, customMinStrategy)).isEqualByComparingTo(new BigDecimal("2.5000"));

        // Stats con minutos negativos
        PlayerStatistics statsNegativeMinutes = mock(PlayerStatistics.class);
        when(statsNegativeMinutes.getMinutosJugados()).thenReturn(-15);
        assertThat(formula.calculate(statsNegativeMinutes, customMinStrategy)).isEqualByComparingTo(new BigDecimal("2.5000"));

        // Stats nulos con estrategia nula (usa minQuote por defecto 1.0000)
        assertThat(formula.calculate(null, null)).isEqualByComparingTo(new BigDecimal("1.0000"));

        // Stats nulos con estrategia que tiene minQuote nulo (usa fallback 1.0000)
        customMinStrategy.setMinQuote(null);
        assertThat(formula.calculate(null, customMinStrategy)).isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFormulasWithExpectedNullStrategyQuote")
    @DisplayName("Cálculo con estrategia nula utiliza ponderadores y minQuote por defecto")
    void formulasUseDefaultWeightsWhenStrategyIsNull(String formulaName, ValuationFormula formula, BigDecimal expectedQuote) {
        PlayerStatistics stats = new PlayerStatistics(
                samplePlayer, 100L, new PlayerStatsDto(
                    4,   // goals
                    3,   // assists
                    10,  // shots
                    8,   // key passes
                    6,   // dribbles
                    12,  // tackles
                    7.0, // rating
                    900, // minutes
                    1,   // yellow cards
                    0    // red cards
            ));

        BigDecimal quote = formula.calculate(stats, null);
        assertThat(quote).isNotNull();
        assertThat(quote.scale()).isEqualTo(4);
        assertThat(quote).isEqualByComparingTo(expectedQuote);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFormulasWithExpectedNullMetricsQuote")
    @DisplayName("Fórmulas manejan métricas nulas en PlayerStatistics sustituyéndolas por cero de forma segura")
    void formulasHandleNullMetricsInStatisticsGracefully(String formulaName, ValuationFormula formula, BigDecimal expectedQuote) {
        PlayerStatistics mockStats = mock(PlayerStatistics.class);
        when(mockStats.getMinutosJugados()).thenReturn(1000);
        when(mockStats.getGoals()).thenReturn(null);
        when(mockStats.getAssists()).thenReturn(null);
        when(mockStats.getShots()).thenReturn(null);
        when(mockStats.getKeyPasses()).thenReturn(null);
        when(mockStats.getDribbles()).thenReturn(null);
        when(mockStats.getTackles()).thenReturn(null);
        when(mockStats.getRating()).thenReturn(null);
        when(mockStats.getTarjetasAmarillas()).thenReturn(null);
        when(mockStats.getTarjetasRojas()).thenReturn(null);

        BigDecimal quote = formula.calculate(mockStats, null);
        assertThat(quote).isNotNull();
        assertThat(quote.scale()).isEqualTo(4);
        assertThat(quote).isEqualByComparingTo(expectedQuote);
    }

    static Stream<Arguments> provideFormulas() {
        return Stream.of(
                Arguments.of("BalancedValuationFormula", new BalancedValuationFormula()),
                Arguments.of("OffensiveValuationFormula", new OffensiveValuationFormula())
        );
    }

    static Stream<Arguments> provideFormulasWithExpectedNullStrategyQuote() {
        // Balanced defaults:
        // goals: 4*1.0=4, assists: 3*0.8=2.4, shots: 10*0.1=1.0, keyPasses: 8*0.2=1.6
        // dribbles: 6*0.05=0.3, tackles: 12*0.05=0.6, minutes: 900*0.01=9.0, rating: 7.0*1.5=10.5
        // cards: 1*(-0.5)=-0.5
        // sum = 4 + 2.4 + 1.0 + 1.6 + 0.3 + 0.6 + 9.0 + 10.5 - 0.5 = 28.9000
        BigDecimal expectedBalanced = new BigDecimal("28.9000");

        // Offensive defaults:
        // goals: 4*1.5=6.0, assists: 3*1.0=3.0, shots: 10*0.2=2.0, keyPasses: 8*0.3=2.4
        // dribbles: 6*0.1=0.6, minutes: 900*0.005=4.5, rating: 7.0*1.2=8.4
        // cards: 1*(-0.3)=-0.3
        // sum = 6.0 + 3.0 + 2.0 + 2.4 + 0.6 + 4.5 + 8.4 - 0.3 = 26.6000
        BigDecimal expectedOffensive = new BigDecimal("26.6000");

        return Stream.of(
                Arguments.of("BalancedValuationFormula", new BalancedValuationFormula(), expectedBalanced),
                Arguments.of("OffensiveValuationFormula", new OffensiveValuationFormula(), expectedOffensive)
        );
    }

    static Stream<Arguments> provideFormulasWithExpectedNullMetricsQuote() {
        // Con solo 1000 minutos y métricas nulas (evaluadas a 0):
        // Balanced default: minutes * 0.01 = 10.0000
        // Offensive default: minutes * 0.005 = 5.0000
        return Stream.of(
                Arguments.of("BalancedValuationFormula", new BalancedValuationFormula(), new BigDecimal("10.0000")),
                Arguments.of("OffensiveValuationFormula", new OffensiveValuationFormula(), new BigDecimal("5.0000"))
        );
    }
}
