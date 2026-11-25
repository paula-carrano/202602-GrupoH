package com.cbo.players.unit;

import com.cbo.players.config.RedisCacheConfig;
import com.cbo.players.dto.response.PlayerRankingResponseDto;
import com.cbo.players.dto.response.RankingItemDto;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerPosition;
import com.cbo.players.model.PlayerQuote;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.model.records.ValuationWeights;
import com.cbo.players.repository.PlayerQuoteRepository;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.ValuationStrategyRepository;
import com.cbo.players.service.RankingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerQuoteRepository playerQuoteRepository;

    @Mock
    private ValuationStrategyRepository valuationStrategyRepository;

    private RankingService rankingService;

    private ValuationStrategy strategy;
    private Player player1;
    private Player player2;
    private Player player3;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(playerRepository, playerQuoteRepository, valuationStrategyRepository);

        strategy = new ValuationStrategy(
                "BALANCED",
                "Rendimiento Global Equilibrado",
                "Estrategia balanceada",
                1,
                true,
                new BigDecimal("1.0000"),
                new ValuationWeights(1.0, 0.8, 0.01, 0.1, 0.2, 1.5, -0.5));

        player1 = new Player("Erling", "Haaland", LocalDate.of(2000, 7, 21),
                "Norwegian", PlayerPosition.FORWARD, "Manchester City", "Premier League");
        player1.setId(1L);

        player2 = new Player("Kylian", "Mbappe", LocalDate.of(1998, 12, 20),
                "French", PlayerPosition.FORWARD, "Real Madrid", "La Liga");
        player2.setId(2L);

        player3 = new Player("Luka", "Modric", LocalDate.of(1985, 9, 9),
                "Croatian", PlayerPosition.MIDFIELDER, "Real Madrid", "La Liga");
        player3.setId(3L);
    }

    @Test
    @DisplayName("US1 - Ranking ordena jugadores de mayor a menor cotización sin consultas N+1")
    void getRanking_ordersDescendingByQuoteWithoutNPlusOne() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(player1, player2, player3));

        PlayerQuote quote1 = new PlayerQuote(player1, new BigDecimal("27.4000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);
        PlayerQuote quote2 = new PlayerQuote(player2, new BigDecimal("32.5000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);
        PlayerQuote quote3 = new PlayerQuote(player3, new BigDecimal("12.0000"), "CRD", LocalDateTime.now(ZoneOffset.UTC), "Balanced", 1);
        when(playerQuoteRepository.findLatestQuotesForActivePlayers()).thenReturn(List.of(quote1, quote2, quote3));

        PlayerRankingResponseDto result = rankingService.getRanking();

        assertThat(result).isNotNull();
        assertThat(result.strategyCode()).isEqualTo("BALANCED");
        assertThat(result.ranking()).hasSize(3);

        List<RankingItemDto> ranking = result.ranking();
        assertThat(ranking.get(0).rank()).isEqualTo(1);
        assertThat(ranking.get(0).playerId()).isEqualTo(2L);
        assertThat(ranking.get(0).currentQuote()).isEqualByComparingTo("32.5000");

        assertThat(ranking.get(1).rank()).isEqualTo(2);
        assertThat(ranking.get(1).playerId()).isEqualTo(1L);
        assertThat(ranking.get(1).currentQuote()).isEqualByComparingTo("27.4000");

        assertThat(ranking.get(2).rank()).isEqualTo(3);
        assertThat(ranking.get(2).playerId()).isEqualTo(3L);
        assertThat(ranking.get(2).currentQuote()).isEqualByComparingTo("12.0000");

        verify(playerQuoteRepository, times(1)).findLatestQuotesForActivePlayers();
        verify(playerQuoteRepository, never()).findFirstByPlayerIdOrderByTimestampDesc(anyLong());
    }

    @Test
    @DisplayName("US1 - Jugador sin cotizaciones registradas toma valor nominal de 1.0")
    void getRanking_defaultQuoteWhenMissing() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(player1));
        when(playerQuoteRepository.findLatestQuotesForActivePlayers()).thenReturn(List.of());

        PlayerRankingResponseDto result = rankingService.getRanking();

        assertThat(result.ranking()).hasSize(1);
        assertThat(result.ranking().get(0).currentQuote()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("US1 - Sin estrategia activa o con versión nula utiliza valores por defecto")
    void getRanking_defaultsWhenNoActiveStrategyOrNullVersion() {
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.empty());

        Player playerWithoutPosition = new Player("Mystery", "Player", LocalDate.of(1995, 5, 5),
                "Unknown", null, "Free Agent", "None");
        playerWithoutPosition.setId(99L);
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(playerWithoutPosition));
        when(playerQuoteRepository.findLatestQuotesForActivePlayers()).thenReturn(List.of());

        PlayerRankingResponseDto resultNoStrategy = rankingService.getRanking();
        assertThat(resultNoStrategy.strategyCode()).isEqualTo("BALANCED");
        assertThat(resultNoStrategy.strategyVersion()).isEqualTo(1);
        assertThat(resultNoStrategy.ranking().get(0).position()).isEmpty();

        ValuationStrategy strategyNullVersion = mock(ValuationStrategy.class);
        when(strategyNullVersion.getCode()).thenReturn("OFFENSIVE");
        when(strategyNullVersion.getVersion()).thenReturn(null);
        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategyNullVersion));

        PlayerRankingResponseDto resultNullVersion = rankingService.getRanking();
        assertThat(resultNullVersion.strategyCode()).isEqualTo("OFFENSIVE");
        assertThat(resultNullVersion.strategyVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("SC-001 - Lecturas repetidas con caché por estrategia logran hit ratio > 90% y latencia < 50ms")
    void getRanking_repeatedReadsAchieveHighHitRatioAndLowLatency() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(RedisCacheConfig.CACHE_RANKING);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RankingService cachedService = new RankingService(
                playerRepository, playerQuoteRepository, valuationStrategyRepository, cacheManager, registry);

        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(player1, player2));
        when(playerQuoteRepository.findLatestQuotesForActivePlayers()).thenReturn(List.of(
                new PlayerQuote(player1, new BigDecimal("25.0000"), "CRD", LocalDateTime.now(ZoneOffset.UTC)),
                new PlayerQuote(player2, new BigDecimal("35.0000"), "CRD", LocalDateTime.now(ZoneOffset.UTC))
        ));

        int totalCalls = 20;
        long maxCachedCallMs = 0L;
        for (int i = 0; i < totalCalls; i++) {
            long start = System.nanoTime();
            PlayerRankingResponseDto response = cachedService.getRanking();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (i > 0) {
                maxCachedCallMs = Math.max(maxCachedCallMs, elapsedMs);
            }
            assertThat(response.ranking()).hasSize(2);
            assertThat(response.ranking().get(0).playerId()).isEqualTo(2L);
        }

        double hits = registry.counter(RankingService.METRIC_CACHE_REQUESTS, "result", "hit").count();
        double misses = registry.counter(RankingService.METRIC_CACHE_REQUESTS, "result", "miss").count();
        double hitRatio = (hits / (hits + misses)) * 100.0;

        assertThat(misses).isEqualTo(1.0);
        assertThat(hits).isEqualTo(totalCalls - 1.0);
        assertThat(hitRatio).isGreaterThan(90.0);
        assertThat(maxCachedCallMs).isLessThan(50L);

        Cache cache = cacheManager.getCache(RedisCacheConfig.CACHE_RANKING);
        assertThat(cache).isNotNull();
        assertThat(cache.get(RankingService.buildCacheKey("BALANCED", 1))).isNotNull();
        verify(playerRepository, times(1)).findAllByActiveTrue();
        verify(playerQuoteRepository, times(1)).findLatestQuotesForActivePlayers();
    }

    @Test
    @DisplayName("US1 - Protección anti-cache stampede evita consultas duplicadas ante ráfagas concurrentes")
    void getRanking_concurrentBurstOnlyQueriesDatabaseOnce() throws Exception {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(RedisCacheConfig.CACHE_RANKING);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RankingService cachedService = new RankingService(
                playerRepository, playerQuoteRepository, valuationStrategyRepository, cacheManager, registry);

        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerRepository.findAllByActiveTrue()).thenAnswer(inv -> {
            Thread.sleep(30);
            return List.of(player1);
        });
        when(playerQuoteRepository.findLatestQuotesForActivePlayers()).thenReturn(List.of());

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PlayerRankingResponseDto>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<PlayerRankingResponseDto> task = () -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return cachedService.getRanking();
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<PlayerRankingResponseDto> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS).ranking()).hasSize(1);
            }
        } finally {
            pool.shutdownNow();
        }

        verify(playerRepository, times(1)).findAllByActiveTrue();
        assertThat(registry.counter(RankingService.METRIC_CACHE_REQUESTS, "result", "miss").count()).isEqualTo(1.0);
        assertThat(registry.counter(RankingService.METRIC_CACHE_REQUESTS, "result", "hit").count()).isEqualTo(threads - 1.0);
    }

    @Test
    @DisplayName("SC-006 - Degradación elegante a PostgreSQL si Redis arroja excepción al leer o escribir")
    void getRanking_fallsBackGracefullyWhenRedisFails() {
        CacheManager failingCacheManager = mock(CacheManager.class);
        Cache failingCache = mock(Cache.class);
        when(failingCacheManager.getCache(RedisCacheConfig.CACHE_RANKING)).thenReturn(failingCache);
        when(failingCache.get(any(), any(Class.class))).thenThrow(new RuntimeException("Redis connection refused"));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RankingService resilientService = new RankingService(
                playerRepository, playerQuoteRepository, valuationStrategyRepository, failingCacheManager, registry);

        when(valuationStrategyRepository.findByActiveTrue()).thenReturn(Optional.of(strategy));
        when(playerRepository.findAllByActiveTrue()).thenReturn(List.of(player1));
        when(playerQuoteRepository.findLatestQuotesForActivePlayers()).thenReturn(List.of(
                new PlayerQuote(player1, new BigDecimal("19.5000"), "CRD", LocalDateTime.now(ZoneOffset.UTC))
        ));

        PlayerRankingResponseDto result = resilientService.getRanking();

        assertThat(result.ranking()).hasSize(1);
        assertThat(result.ranking().get(0).currentQuote()).isEqualByComparingTo("19.5000");
    }

    @Test
    @DisplayName("US1 - Serializador JSON de Redis preserva tipos Java record y strings simples")
    void redisObjectMapper_roundTripsRecordsAndStrings() {
        ObjectMapper redisMapper = RedisCacheConfig.buildRedisObjectMapper();
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisMapper);

        PlayerRankingResponseDto dto = new PlayerRankingResponseDto(
                "OFFENSIVE",
                2,
                true,
                List.of(new RankingItemDto(1, 10L, "Kylian", "Mbappe", "FORWARD", "Real Madrid", "La Liga",
                        new BigDecimal("42.5000"), "CRD"))
        );

        byte[] serializedDto = serializer.serialize(dto);
        Object deserializedDto = serializer.deserialize(serializedDto);
        assertThat(deserializedDto).isInstanceOf(PlayerRankingResponseDto.class);
        assertThat(((PlayerRankingResponseDto) deserializedDto).ranking().get(0).currentQuote())
                .isEqualByComparingTo("42.5000");

        byte[] serializedString = serializer.serialize("some_cached_data");
        assertThat(serializer.deserialize(serializedString)).isEqualTo("some_cached_data");
    }
}
