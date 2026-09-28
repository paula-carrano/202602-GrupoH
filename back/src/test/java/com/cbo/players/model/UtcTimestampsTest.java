package com.cbo.players.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import static org.assertj.core.api.Assertions.assertThat;

class UtcTimestampsTest {
    @Test
    void lifecycleTimestampsUseUtcRegardlessOfServerTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
            LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
            User user = new User();
            user.onCreate();
            LocalDateTime created = user.getCreatedAt();
            user.setUpdatedAt(before.minusDays(1));
            user.onUpdate();
            ApiKey key = new ApiKey();
            key.onCreate();
            PlayerQuote quote = new PlayerQuote(null, BigDecimal.ONE, null, null);
            LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
            assertThat(created).isBetween(before, after);
            assertThat(user.getCreatedAt()).isEqualTo(created);
            assertThat(user.getUpdatedAt()).isBetween(before, after);
            assertThat(key.getCreatedAt()).isBetween(before, after);
            assertThat(quote.getTimestamp()).isBetween(before, after);
            assertThat(quote.getCurrency()).isEqualTo("CRD");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void quotePreservesExplicitTimestampAndCurrency() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 2, 12, 30);
        PlayerQuote quote = new PlayerQuote(null, BigDecimal.TEN, "USD", timestamp);
        assertThat(quote.getTimestamp()).isEqualTo(timestamp);
        assertThat(quote.getCurrency()).isEqualTo("USD");
    }
}
