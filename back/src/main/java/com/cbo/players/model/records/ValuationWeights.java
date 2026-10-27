package com.cbo.players.model.records;

public record ValuationWeights(
        Double goals,
        Double assists,
        Double minutes,
        Double shots,
        Double keyPasses,
        Double rating,
        Double cards
) {}
