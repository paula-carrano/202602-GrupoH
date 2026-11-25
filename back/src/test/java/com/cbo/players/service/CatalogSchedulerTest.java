package com.cbo.players.service;

import com.cbo.players.adapter.ScrapingPort;
import com.cbo.players.adapter.dto.CatalogTeamDto;
import com.cbo.players.adapter.dto.PlayerStatsDto;
import com.cbo.players.adapter.dto.SquadPlayerDto;
import com.cbo.players.adapter.dto.TeamSquadDto;
import com.cbo.players.config.RedisCacheConfig;
import com.cbo.players.exception.ApiException;
import com.cbo.players.exception.ErrorCode;
import com.cbo.players.model.CatalogSyncRun;
import com.cbo.players.model.Player;
import com.cbo.players.model.PlayerStatistics;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.repository.PlayerStatisticsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSchedulerTest {

    @Test
    void schedulesAndOnlyIgnoresOverlap() {
        var sync = mock(CatalogSyncService.class);
        var scheduler = new CatalogScheduler(sync);
        scheduler.synchronize();
        verify(sync).start(CatalogSyncRun.Origin.SCHEDULED);
        when(sync.start(CatalogSyncRun.Origin.SCHEDULED)).thenThrow(
                new ApiException("busy", HttpStatus.CONFLICT, ErrorCode.CATALOG_SYNC_BUSY));
        assertDoesNotThrow(scheduler::synchronize);
        doThrow(new ApiException("failure", HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED))
                .when(sync).start(CatalogSyncRun.Origin.SCHEDULED);
        assertThrows(ApiException.class, scheduler::synchronize);
    }

    @Test
    void schedulesAndExecutesQuoteRecalculationWhenPresent() {
        var sync = mock(CatalogSyncService.class);
        var quoteCalc = mock(QuoteCalculationService.class);
        var scheduler = new CatalogScheduler(sync, quoteCalc);

        scheduler.synchronize();

        verify(sync).start(CatalogSyncRun.Origin.SCHEDULED);
        verify(quoteCalc).recalculateAllQuotes();
    }

    @Test
    void schedulesCatchesAndLogsExceptionWhenQuoteRecalculationFails() {
        var sync = mock(CatalogSyncService.class);
        var quoteCalc = mock(QuoteCalculationService.class);
        doThrow(new RuntimeException("Valuation formula calculation error")).when(quoteCalc).recalculateAllQuotes();

        var scheduler = new CatalogScheduler(sync, quoteCalc);

        assertDoesNotThrow(scheduler::synchronize);
        verify(sync).start(CatalogSyncRun.Origin.SCHEDULED);
        verify(quoteCalc).recalculateAllQuotes();
    }

    @Test
    @DisplayName("FR-013 - Scheduler de scraping espera la persistencia en DB, invalida cachés y luego recalcula cotizaciones")
    void schedulerWaitsForScrapingPersistenceEvictsCachesAndThenRecalculatesQuotes() {
        ScrapingPort scraper = mock(ScrapingPort.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlayerStatisticsRepository statsRepository = mock(PlayerStatisticsRepository.class);
        CatalogRunService runs = mock(CatalogRunService.class);
        QuoteCalculationService quoteCalc = mock(QuoteCalculationService.class);
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                RedisCacheConfig.CACHE_CATALOG, RedisCacheConfig.CACHE_RANKING);
        cacheManager.getCache(RedisCacheConfig.CACHE_CATALOG).put(PlayerService.ALL_PLAYERS_CACHE_KEY, List.of());
        cacheManager.getCache(RedisCacheConfig.CACHE_RANKING).put("active_ranking:BALANCED:v1", "stale");

        List<String> executionOrder = new ArrayList<>();

        Player existingPlayer = new Player();
        existingPlayer.setId(5L);
        existingPlayer.setFootballDataId(500L);
        existingPlayer.setWhoscoredId(300713L);
        existingPlayer.setActive(true);

        when(runs.create(CatalogSyncRun.Origin.SCHEDULED)).thenReturn(1L);
        when(scraper.getCompetitionTeams("PL")).thenReturn(List.of(new CatalogTeamDto(10L, "Arsenal")));
        when(scraper.getCompetitionTeams("BL1")).thenReturn(List.of());
        when(scraper.getCompetitionTeams("PD")).thenReturn(List.of());
        when(scraper.getCompetitionTeams("SA")).thenReturn(List.of());
        when(scraper.getCompetitionTeams("FL1")).thenReturn(List.of());
        when(scraper.getTeamSquad(10L)).thenReturn(new TeamSquadDto(
                new CatalogTeamDto(10L, "Arsenal"),
                List.of(new SquadPlayerDto(500L, "Kylian Mbappe", "Kylian", "Mbappe",
                        LocalDate.of(1998, 12, 20), "France", "Forward"))
        ));
        when(playerRepository.findByFootballDataId(500L)).thenReturn(Optional.of(existingPlayer));
        when(playerRepository.findLockedById(5L)).thenReturn(Optional.of(existingPlayer));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
            executionOrder.add("PERSIST_PLAYER");
            return inv.getArgument(0);
        });
        when(playerRepository.findAllByActiveTrueAndWhoscoredIdIsNotNull()).thenReturn(List.of(existingPlayer));
        when(scraper.getPlayerStats(300713L)).thenReturn(
                new PlayerStatsDto(20, 8, 60, 30, 40, 5, 8.05, 2100, 2, 0));
        when(statsRepository.findById(5L)).thenReturn(Optional.empty());
        when(statsRepository.saveAndFlush(any(PlayerStatistics.class))).thenAnswer(inv -> {
            executionOrder.add("PERSIST_STATS");
            return inv.getArgument(0);
        });
        doAnswer(inv -> {
            executionOrder.add("RECALCULATE_QUOTES");
            return null;
        }).when(quoteCalc).recalculateAllQuotes();

        CatalogPersistenceService persistence = new CatalogPersistenceService(
                playerRepository, statsRepository, cacheManager);

        ExecutorService asyncExecutor = Executors.newSingleThreadExecutor();
        try {
            CatalogSyncService syncService = new CatalogSyncService(scraper, persistence, runs, asyncExecutor);
            CatalogScheduler scheduler = new CatalogScheduler(syncService, quoteCalc);

            scheduler.synchronize();
        } finally {
            asyncExecutor.shutdownNow();
        }

        assertThat(executionOrder).containsExactly("PERSIST_PLAYER", "PERSIST_STATS", "RECALCULATE_QUOTES");
        assertThat(cacheManager.getCache(RedisCacheConfig.CACHE_CATALOG).get(PlayerService.ALL_PLAYERS_CACHE_KEY)).isNull();
        assertThat(cacheManager.getCache(RedisCacheConfig.CACHE_RANKING).get("active_ranking:BALANCED:v1")).isNull();
        assertThat(existingPlayer.getCurrentTeam()).isEqualTo("Arsenal");
        assertThat(existingPlayer.getLastCatalogSyncAt()).isNotNull();
    }
}
