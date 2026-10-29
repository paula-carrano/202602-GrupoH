package com.cbo.players.unit;

import com.cbo.players.adapter.dto.PlayerStatsDto;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.model.Transaction;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.model.records.TransactionData;
import com.cbo.players.model.records.ValuationWeights;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EntityConstructionTest {
    @Test
    void statisticsPreserveMetricsAndDefaultSourceId() {
        PlayerStatsDto metrics = new PlayerStatsDto(1, 2, 3, 4, 5, 6, 7.5, 90, 8, 9);
        PlayerStatistics stats = new PlayerStatistics(null, null, metrics);

        assertThat(stats.getWhoscoredId()).isZero();
        assertThat(stats.metrics()).isEqualTo(metrics);
        assertThat(stats.getLastSuccessfulSyncAt()).isNotNull();
    }

    @Test
    void topupPreservesOptionalFieldsBalancesAndDefaultVersion() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        Transaction tx = new Transaction(new TransactionData(
                10L, OperationType.RECARGA, null, null, null,
                new BigDecimal("25.0000"), new BigDecimal("100.0000"),
                new BigDecimal("125.0000"), null, null, null));

        assertThat(tx.getActorUserId()).isEqualTo(10L);
        assertThat(tx.getOperationType()).isEqualTo(OperationType.RECARGA);
        assertThat(tx.getPlayerId()).isNull();
        assertThat(tx.getQuantity()).isNull();
        assertThat(tx.getUnitPrice()).isNull();
        assertThat(tx.getTokensBefore()).isNull();
        assertThat(tx.getTokensAfter()).isNull();
        assertThat(tx.getTotalAmount()).isEqualByComparingTo("25.0000");
        assertThat(tx.getBalanceBefore()).isEqualByComparingTo("100.0000");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("125.0000");
        assertThat(tx.getStrategyVersion()).isEqualTo(1);
        assertThat(tx.getTimestamp()).isBetween(before, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void strategyRoundsMinimumAndPreservesMixedDefaultWeights() {
        ValuationStrategy strategy = new ValuationStrategy(
                "CUSTOM", "Custom", "Description", 3, true, new BigDecimal("1.23455"),
                new ValuationWeights(2.0, null, null, null, null, null, -0.8));

        assertThat(strategy.getMinQuote()).isEqualTo(new BigDecimal("1.2346"));
        assertThat(strategy.getVersion()).isEqualTo(3);
        assertThat(strategy.getWeightGoals()).isEqualTo(2.0);
        assertThat(strategy.getWeightAssists()).isEqualTo(0.8);
        assertThat(strategy.getWeightCards()).isEqualTo(-0.8);
    }

    @Test
    void constructorsRejectNullParameterObjects() {
        assertThatNullPointerException().isThrownBy(() -> new PlayerStatistics(null, 1L, null));
        assertThatNullPointerException().isThrownBy(() -> new Transaction(null));
        assertThatNullPointerException().isThrownBy(() -> new ValuationStrategy(
                "CUSTOM", "Custom", "Description", 1, true, BigDecimal.ONE, null));
    }
}
