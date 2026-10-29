package com.cbo.players.dto.response;

import com.cbo.players.model.ValuationStrategy;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record StrategyResponseDto(
        Long id,
        String code,
        String name,
        String description,
        Integer version,
        Boolean active,
        BigDecimal minQuote,
        Map<String, Double> weights,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime updatedAt
) {
    public static StrategyResponseDto from(ValuationStrategy s) {
        return new StrategyResponseDto(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getDescription(),
                s.getVersion(),
                s.isActive(),
                s.getMinQuote(),
                s.getWeights(),
                s.getUpdatedAt()
        );
    }
}
