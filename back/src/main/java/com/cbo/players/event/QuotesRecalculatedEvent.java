package com.cbo.players.event;

import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.ValuationStrategy;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class QuotesRecalculatedEvent extends ApplicationEvent {

    private final List<PlayerQuote> quotes;
    private final ValuationStrategy strategy;
    private final LocalDateTime calculationTimestamp;

    public QuotesRecalculatedEvent(Object source, List<PlayerQuote> quotes, ValuationStrategy strategy, LocalDateTime calculationTimestamp) {
        super(source);
        this.quotes = quotes != null ? List.copyOf(quotes) : Collections.emptyList();
        this.strategy = strategy;
        this.calculationTimestamp = calculationTimestamp;
    }

    public List<PlayerQuote> getQuotes() {
        return quotes;
    }

    public ValuationStrategy getStrategy() {
        return strategy;
    }

    public LocalDateTime getCalculationTimestamp() {
        return calculationTimestamp;
    }
}
