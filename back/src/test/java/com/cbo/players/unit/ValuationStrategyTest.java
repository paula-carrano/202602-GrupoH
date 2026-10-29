package com.cbo.players.unit;

import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.model.ValuationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValuationStrategyTest {

    @Test
    @DisplayName("Constructor con valores nulos inicializa con valores por defecto")
    void constructorWithNullsAppliesDefaults() {
        ValuationStrategy strategy = new ValuationStrategy(
                "CUSTOM", "Custom Strategy", "Description",
                null, // version
                true,
                null, // minQuote
                new ValuationWeights(
                    null, // weightGoals
                    null, // weightAssists
                    null, // weightMinutes
                    null, // weightShots
                    null, // weightKeyPasses
                    null, // weightRating
                    null  // weightCards
            ));

        assertThat(strategy.getCode()).isEqualTo("CUSTOM");
        assertThat(strategy.getName()).isEqualTo("Custom Strategy");
        assertThat(strategy.getDescription()).isEqualTo("Description");
        assertThat(strategy.getVersion()).isEqualTo(1);
        assertThat(strategy.isActive()).isTrue();
        assertThat(strategy.getMinQuote()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(strategy.getWeightGoals()).isEqualTo(1.0);
        assertThat(strategy.getWeightAssists()).isEqualTo(0.8);
        assertThat(strategy.getWeightMinutes()).isEqualTo(0.01);
        assertThat(strategy.getWeightShots()).isEqualTo(0.1);
        assertThat(strategy.getWeightKeyPasses()).isEqualTo(0.2);
        assertThat(strategy.getWeightRating()).isEqualTo(1.5);
        assertThat(strategy.getWeightCards()).isEqualTo(-0.5);
        assertThat(strategy.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("getWeights devuelve el mapa con todas las ponderaciones en orden esperado")
    void getWeightsReturnsCorrectMap() {
        ValuationStrategy strategy = new ValuationStrategy(
                "BALANCED", "Balanced", "Desc", 2, true, new BigDecimal("1.0000"), new ValuationWeights(
                    1.1, 0.9, 0.02, 0.15, 0.25, 1.6, -0.4
            ));

        Map<String, Double> weights = strategy.getWeights();
        assertThat(weights).containsEntry("goals", 1.1)
                .containsEntry("assists", 0.9)
                .containsEntry("minutes", 0.02)
                .containsEntry("shots", 0.15)
                .containsEntry("keyPasses", 0.25)
                .containsEntry("rating", 1.6)
                .containsEntry("cards", -0.4);
    }

    @Test
    @DisplayName("Lifecycle callbacks onCreate y onUpdate configuran timestamp y minQuote")
    void lifecycleCallbacksWorkCorrectly() throws Exception {
        ValuationStrategy strategy = new ValuationStrategy();
        strategy.setCode("OFFENSIVE");

        // Invocar onCreate via reflection si es protected
        Method onCreate = ValuationStrategy.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(strategy);

        assertThat(strategy.getUpdatedAt()).isNotNull();
        assertThat(strategy.getMinQuote()).isEqualByComparingTo(new BigDecimal("1.0000"));

        LocalDateTime beforeUpdate = strategy.getUpdatedAt();
        Method onUpdate = ValuationStrategy.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);
        onUpdate.invoke(strategy);

        assertThat(strategy.getUpdatedAt()).isNotNull();
        assertThat(strategy.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    @DisplayName("Setters y getters actualizan y devuelven las propiedades correctamente")
    void settersAndGettersWork() {
        ValuationStrategy strategy = new ValuationStrategy();
        strategy.setId(10L);
        strategy.setCode("TEST_CODE");
        strategy.setName("New Name");
        strategy.setDescription("New Desc");
        strategy.setVersion(3);
        strategy.setActive(false);
        strategy.setMinQuote(new BigDecimal("3.0000"));
        strategy.setWeightGoals(2.0);
        strategy.setWeightAssists(1.5);
        strategy.setWeightMinutes(0.05);
        strategy.setWeightShots(0.3);
        strategy.setWeightKeyPasses(0.4);
        strategy.setWeightRating(1.8);
        strategy.setWeightCards(-0.8);
        LocalDateTime now = LocalDateTime.now();
        strategy.setUpdatedAt(now);

        assertThat(strategy.getId()).isEqualTo(10L);
        assertThat(strategy.getCode()).isEqualTo("TEST_CODE");
        assertThat(strategy.getName()).isEqualTo("New Name");
        assertThat(strategy.getDescription()).isEqualTo("New Desc");
        assertThat(strategy.getVersion()).isEqualTo(3);
        assertThat(strategy.isActive()).isFalse();
        assertThat(strategy.getMinQuote()).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(strategy.getWeightGoals()).isEqualTo(2.0);
        assertThat(strategy.getWeightAssists()).isEqualTo(1.5);
        assertThat(strategy.getWeightMinutes()).isEqualTo(0.05);
        assertThat(strategy.getWeightShots()).isEqualTo(0.3);
        assertThat(strategy.getWeightKeyPasses()).isEqualTo(0.4);
        assertThat(strategy.getWeightRating()).isEqualTo(1.8);
        assertThat(strategy.getWeightCards()).isEqualTo(-0.8);
        assertThat(strategy.getUpdatedAt()).isEqualTo(now);
    }
}
