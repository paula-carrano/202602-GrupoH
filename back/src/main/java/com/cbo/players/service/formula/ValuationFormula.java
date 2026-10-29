package com.cbo.players.service.formula;

import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.model.ValuationStrategy;
import java.math.BigDecimal;

public interface ValuationFormula {
    String getStrategyCode();
    BigDecimal calculate(PlayerStatistics stats, ValuationStrategy strategy);
}
