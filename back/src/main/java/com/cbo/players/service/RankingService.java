package com.cbo.players.service;

import com.cbo.players.config.RedisCacheConfig;
import com.cbo.players.dto.response.PlayerRankingResponseDto;
import com.cbo.players.dto.response.RankingItemDto;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
public class RankingService {

    public static final String METRIC_CACHE_REQUESTS = "cbo.ranking.cache.requests";
    public static final String METRIC_QUERY_DURATION = "cbo.ranking.query.duration";

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private static final BigDecimal DEFAULT_QUOTE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository playerQuoteRepository;
    private final ValuationStrategyRepository valuationStrategyRepository;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public RankingService(PlayerRepository playerRepository,
                          PlayerQuoteRepository playerQuoteRepository,
                          ValuationStrategyRepository valuationStrategyRepository) {
        this(playerRepository, playerQuoteRepository, valuationStrategyRepository, null, null);
    }

    @Autowired
    public RankingService(PlayerRepository playerRepository,
                          PlayerQuoteRepository playerQuoteRepository,
                          ValuationStrategyRepository valuationStrategyRepository,
                          @Autowired(required = false) CacheManager cacheManager,
                          @Autowired(required = false) MeterRegistry meterRegistry) {
        this.playerRepository = playerRepository;
        this.playerQuoteRepository = playerQuoteRepository;
        this.valuationStrategyRepository = valuationStrategyRepository;
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
    }

    public static String buildCacheKey(String strategyCode, int strategyVersion) {
        return "active_ranking:" + strategyCode + ":v" + strategyVersion;
    }

    public PlayerRankingResponseDto getRanking() {
        long startNanos = System.nanoTime();
        ValuationStrategy activeStrategy = valuationStrategyRepository.findByActiveTrue().orElse(null);
        String strategyCode = activeStrategy != null ? activeStrategy.getCode() : "BALANCED";
        int strategyVersion = activeStrategy != null && activeStrategy.getVersion() != null ? activeStrategy.getVersion() : 1;
        String cacheKey = buildCacheKey(strategyCode, strategyVersion);

        PlayerRankingResponseDto cached = readFromCache(cacheKey);
        if (cached != null) {
            recordMetrics("hit", "cache", startNanos);
            return cached;
        }

        Object lock = keyLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            PlayerRankingResponseDto doubleChecked = readFromCache(cacheKey);
            if (doubleChecked != null) {
                recordMetrics("hit", "cache", startNanos);
                return doubleChecked;
            }

            PlayerRankingResponseDto computed = computeRankingFromDatabase(strategyCode, strategyVersion);
            writeToCache(cacheKey, computed);
            recordMetrics("miss", "db", startNanos);
            return computed;
        }
    }

    private PlayerRankingResponseDto computeRankingFromDatabase(String strategyCode, int strategyVersion) {
        List<Player> activePlayers = playerRepository.findAllByActiveTrue();
        List<PlayerQuote> latestQuotes = playerQuoteRepository.findLatestQuotesForActivePlayers();

        Map<Long, BigDecimal> latestQuoteByPlayerId = new HashMap<>();
        for (PlayerQuote quote : latestQuotes) {
            if (quote != null && quote.getPlayer() != null && quote.getPlayer().getId() != null) {
                latestQuoteByPlayerId.putIfAbsent(quote.getPlayer().getId(), quote.getPrice());
            }
        }

        record PlayerWithQuote(Player player, BigDecimal quote) {}

        List<PlayerWithQuote> sorted = activePlayers.stream()
                .map(p -> new PlayerWithQuote(p, latestQuoteByPlayerId.getOrDefault(p.getId(), DEFAULT_QUOTE)))
                .sorted(Comparator.comparing(PlayerWithQuote::quote, Comparator.reverseOrder())
                        .thenComparing(item -> item.player().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<RankingItemDto> rankingItems = new ArrayList<>(sorted.size());
        int rank = 1;
        for (PlayerWithQuote item : sorted) {
            Player p = item.player();
            String pos = p.getPosition() != null ? p.getPosition().name() : "";
            rankingItems.add(new RankingItemDto(
                    rank++,
                    p.getId(),
                    p.getFirstName(),
                    p.getLastName(),
                    pos,
                    p.getCurrentTeam(),
                    p.getLeague(),
                    item.quote(),
                    "CRD"
            ));
        }

        return new PlayerRankingResponseDto(strategyCode, strategyVersion, true, rankingItems);
    }

    private PlayerRankingResponseDto readFromCache(String cacheKey) {
        if (cacheManager == null) {
            return null;
        }
        try {
            Cache cache = cacheManager.getCache(RedisCacheConfig.CACHE_RANKING);
            if (cache == null) {
                return null;
            }
            return cache.get(cacheKey, PlayerRankingResponseDto.class);
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer el ranking desde caché Redis (key={}). Continuando con PostgreSQL: {}",
                    cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeToCache(String cacheKey, PlayerRankingResponseDto value) {
        if (cacheManager == null || value == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(RedisCacheConfig.CACHE_RANKING);
            if (cache != null) {
                cache.put(cacheKey, value);
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo escribir el ranking en caché Redis (key={}). Continuando sin caché: {}",
                    cacheKey, ex.getMessage());
        }
    }

    private void recordMetrics(String cacheResult, String source, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
        meterRegistry.counter(METRIC_CACHE_REQUESTS, "result", cacheResult).increment();
        meterRegistry.timer(METRIC_QUERY_DURATION, "source", source).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }
}
