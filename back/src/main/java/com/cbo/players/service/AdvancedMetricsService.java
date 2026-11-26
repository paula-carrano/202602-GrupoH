package com.cbo.players.service;

import com.cbo.players.dto.response.AdvancedMetricsResponseDto;
import com.cbo.players.dto.response.AlertMetricsDto;
import com.cbo.players.dto.response.MarketMetricsDto;
import com.cbo.players.dto.response.PerformanceMetricsDto;
import com.cbo.players.dto.response.TopTradedPlayerDto;
import com.cbo.players.dto.response.ValuationMetricsDto;
import com.cbo.players.model.AlertStatus;
import com.cbo.players.model.OperationType;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.UserRole;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PortfolioPositionRepository;
import com.cbo.players.repository.PriceAlertRepository;
import com.cbo.players.repository.TransactionRepository;
import com.cbo.players.repository.UserRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdvancedMetricsService {

    private static final long TOKENS_PER_PLAYER = 100L;
    private static final int TOP_TRADED_LIMIT = 5;
    private static final List<OperationType> TRADING_OPERATIONS = List.of(OperationType.COMPRA, OperationType.VENTA);
    private static final BigDecimal ZERO_CREDITS = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_QUOTE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final TransactionRepository transactionRepository;
    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository playerQuoteRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final ValuationStrategyRepository valuationStrategyRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public AdvancedMetricsService(
            TransactionRepository transactionRepository,
            PlayerRepository playerRepository,
            PlayerQuoteRepository playerQuoteRepository,
            PortfolioPositionRepository portfolioPositionRepository,
            ValuationStrategyRepository valuationStrategyRepository,
            PriceAlertRepository priceAlertRepository,
            UserRepository userRepository,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.playerRepository = playerRepository;
        this.playerQuoteRepository = playerQuoteRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.valuationStrategyRepository = valuationStrategyRepository;
        this.priceAlertRepository = priceAlertRepository;
        this.userRepository = userRepository;
        this.meterRegistry = meterRegistry;
    }

    public AdvancedMetricsResponseDto getAdvancedMetrics() {
        List<Player> activePlayers = playerRepository.findAllByActiveTrue();
        MarketMetricsDto market = buildMarketMetrics(activePlayers.size());
        ValuationMetricsDto valuation = buildValuationMetrics(activePlayers);
        AlertMetricsDto alerts = buildAlertMetrics();
        PerformanceMetricsDto performance = buildPerformanceMetrics();
        return new AdvancedMetricsResponseDto(market, valuation, alerts, performance, Instant.now());
    }

    private MarketMetricsDto buildMarketMetrics(int activePlayerCount) {
        Map<String, Long> byType = new LinkedHashMap<>();
        long totalTransactions = 0L;
        for (OperationType type : OperationType.values()) {
            long count = transactionRepository.countByOperationType(type);
            byType.put(type.name(), count);
            totalTransactions += count;
        }

        BigDecimal tradedCredits = transactionRepository.sumTotalAmountByOperationTypes(TRADING_OPERATIONS);
        BigDecimal normalizedCredits = tradedCredits != null
                ? tradedCredits.setScale(4, RoundingMode.HALF_UP)
                : ZERO_CREDITS;

        long activeUsers = userRepository.countByEnabledTrue();
        long totalIssuedTokens = activePlayerCount * TOKENS_PER_PLAYER;
        Long circulationRaw = portfolioPositionRepository.sumTokensExcludingUserRole(UserRole.ROLE_SUPERUSER);
        long tokensInCirculation = circulationRaw != null ? Math.max(0L, circulationRaw) : 0L;
        double circulationRatio = totalIssuedTokens > 0
                ? roundTwoDecimals((tokensInCirculation * 100.0) / totalIssuedTokens)
                : 0.0;

        List<TopTradedPlayerDto> topTraded = buildTopTradedPlayers();

        return new MarketMetricsDto(
                totalTransactions,
                byType,
                normalizedCredits,
                activeUsers,
                totalIssuedTokens,
                tokensInCirculation,
                circulationRatio,
                topTraded
        );
    }

    private List<TopTradedPlayerDto> buildTopTradedPlayers() {
        List<Object[]> rawRows = transactionRepository.findTopTradedPlayersRaw(
                TRADING_OPERATIONS, PageRequest.of(0, TOP_TRADED_LIMIT));
        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }

        List<Long> playerIds = rawRows.stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
        Map<Long, Player> playersById = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity(), (a, b) -> a));

        List<TopTradedPlayerDto> top = new ArrayList<>(rawRows.size());
        for (Object[] row : rawRows) {
            Long playerId = ((Number) row[0]).longValue();
            long tokens = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            BigDecimal volume = row[2] instanceof BigDecimal bd
                    ? bd.setScale(4, RoundingMode.HALF_UP)
                    : ZERO_CREDITS;
            Player player = playersById.get(playerId);
            String fullName = player != null
                    ? (player.getFirstName() + " " + player.getLastName()).trim()
                    : "Jugador #" + playerId;
            String team = player != null && player.getCurrentTeam() != null ? player.getCurrentTeam() : "";
            top.add(new TopTradedPlayerDto(playerId, fullName, team, tokens, volume));
        }
        return top;
    }

    private ValuationMetricsDto buildValuationMetrics(List<Player> activePlayers) {
        ValuationStrategy strategy = valuationStrategyRepository.findByActiveTrue().orElse(null);
        String strategyCode = strategy != null ? strategy.getCode() : "BALANCED";
        String strategyName = strategy != null ? strategy.getName() : "Rendimiento Global Equilibrado";
        int strategyVersion = strategy != null && strategy.getVersion() != null ? strategy.getVersion() : 1;

        LocalDateTime lastRecalc = playerQuoteRepository.findMaxTimestamp().orElse(null);
        long coveredPlayers = activePlayers.size();

        if (coveredPlayers == 0) {
            return new ValuationMetricsDto(
                    strategyCode, strategyName, strategyVersion, lastRecalc,
                    0L, ZERO_CREDITS, ZERO_CREDITS, ZERO_CREDITS);
        }

        Map<Long, BigDecimal> latestByPlayer = loadLatestPricesByPlayer();

        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        for (Player player : activePlayers) {
            BigDecimal price = latestByPlayer.getOrDefault(player.getId(), DEFAULT_QUOTE);
            sum = sum.add(price);
            if (min == null || price.compareTo(min) < 0) {
                min = price;
            }
            if (max == null || price.compareTo(max) > 0) {
                max = price;
            }
        }

        BigDecimal avg = sum.divide(BigDecimal.valueOf(coveredPlayers), 4, RoundingMode.HALF_UP);
        return new ValuationMetricsDto(
                strategyCode,
                strategyName,
                strategyVersion,
                lastRecalc,
                coveredPlayers,
                avg,
                min != null ? min.setScale(4, RoundingMode.HALF_UP) : ZERO_CREDITS,
                max != null ? max.setScale(4, RoundingMode.HALF_UP) : ZERO_CREDITS
        );
    }

    private Map<Long, BigDecimal> loadLatestPricesByPlayer() {
        Map<Long, BigDecimal> prices = new HashMap<>();
        for (PlayerQuote quote : playerQuoteRepository.findLatestQuotesForActivePlayers()) {
            if (quote != null
                    && quote.getPlayer() != null
                    && quote.getPlayer().getId() != null
                    && quote.getPrice() != null) {
                prices.putIfAbsent(quote.getPlayer().getId(), quote.getPrice());
            }
        }
        return prices;
    }

    private AlertMetricsDto buildAlertMetrics() {
        long active = priceAlertRepository.countByStatus(AlertStatus.ACTIVA);
        long triggered = priceAlertRepository.countByStatus(AlertStatus.DISPARADA);
        return new AlertMetricsDto(active, triggered);
    }

    private PerformanceMetricsDto buildPerformanceMetrics() {
        if (meterRegistry == null) {
            return new PerformanceMetricsDto(0L, 0L, 0.0, 0.0);
        }

        long hits = Math.round(sumCounter(RankingService.METRIC_CACHE_REQUESTS, "result", "hit"));
        long misses = Math.round(sumCounter(RankingService.METRIC_CACHE_REQUESTS, "result", "miss"));
        long total = hits + misses;
        double hitRatio = total > 0 ? roundTwoDecimals((hits * 100.0) / total) : 0.0;

        Collection<Timer> timers = meterRegistry.find(RankingService.METRIC_QUERY_DURATION).timers();
        long totalCount = 0L;
        double totalMs = 0.0;
        for (Timer timer : timers) {
            totalCount += timer.count();
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
        }
        double avgLatencyMs = totalCount > 0 ? roundTwoDecimals(totalMs / totalCount) : 0.0;

        return new PerformanceMetricsDto(hits, misses, hitRatio, avgLatencyMs);
    }

    private double sumCounter(String name, String tagKey, String tagValue) {
        Collection<Counter> counters = meterRegistry.find(name).tag(tagKey, tagValue).counters();
        double sum = 0.0;
        for (Counter counter : counters) {
            sum += counter.count();
        }
        return sum;
    }

    private static double roundTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
