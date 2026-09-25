package com.cbo.players.service;

import com.cbo.players.adapter.*;
import com.cbo.players.exception.*;
import com.cbo.players.model.CatalogSyncRun;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@org.springframework.context.annotation.DependsOn("initializeCatalogSchema")
public class CatalogSyncService {
    private static final List<String> LEAGUES = List.of("PL", "BL1", "PD", "SA", "FL1");
    private final ScrapingPort scraper;
    private final CatalogPersistenceService catalog;
    private final CatalogRunService runs;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    public CatalogSyncService(ScrapingPort scraper, CatalogPersistenceService catalog, CatalogRunService runs,
            @Qualifier("catalogSyncExecutor") Executor executor) {
        this.scraper = scraper; this.catalog = catalog; this.runs = runs; this.executor = executor;
    }

    @PostConstruct
    void recover() { runs.recoverInterrupted(); }

    public Long start(CatalogSyncRun.Origin origin) {
        if (!running.compareAndSet(false, true)) {
            throw new ApiException("Ya hay una sincronizacion en curso", HttpStatus.CONFLICT, ErrorCode.CATALOG_SYNC_BUSY);
        }
        Long id = null;
        try {
            id = runs.create(origin);
            Long runId = id;
            executor.execute(() -> execute(runId));
            return id;
        } catch (RuntimeException error) {
            running.set(false);
            if (id != null) runs.finish(id, true);
            throw error;
        }
    }

    private void execute(Long id) {
        boolean interrupted = false;
        try {
            importCatalog(id);
            updateStatistics(id);
        } catch (SyncInterrupted error) {
            interrupted = true;
        } catch (RuntimeException error) {
            runs.error(id, "RUN: INTERNAL_ERROR");
        } finally {
            try { runs.finish(id, interrupted || Thread.currentThread().isInterrupted()); }
            finally { running.set(false); }
        }
    }

    private void importCatalog(Long id) {
        for (String league : LEAGUES) {
            checkInterrupted();
            List<com.cbo.players.adapter.dto.CatalogTeamDto> teams;
            try { teams = scraper.getCompetitionTeams(league); }
            catch (RuntimeException error) {
                report(id, league, error);
                if (stopProvider(error)) return;
                continue;
            }
            for (var team : teams) {
                checkInterrupted();
                try {
                    var squad = scraper.getTeamSquad(team.id());
                    checkInterrupted();
                    runs.imported(id, catalog.importSquad(league, squad));
                } catch (SyncInterrupted error) { throw error; }
                catch (RuntimeException error) {
                    report(id, league + "/team/" + team.id(), error);
                    if (stopProvider(error)) return;
                }
            }
        }
    }

    private void updateStatistics(Long id) {
        for (var link : catalog.linkedPlayers()) {
            checkInterrupted();
            try {
                var stats = scraper.getPlayerStats(link.whoscoredId());
                checkInterrupted();
                if (catalog.saveStatistics(link, stats)) runs.statisticsUpdated(id);
            } catch (SyncInterrupted error) { throw error; }
            catch (RuntimeException error) {
                report(id, "player/" + link.playerId(), error);
                if (stopProvider(error) || error instanceof ScrapingException scrape
                        && "SCRAPE_BLOCKED".equals(scrape.getRemoteCode())) return;
            }
        }
    }

    private void report(Long id, String scope, RuntimeException error) {
        // Only controlled codes; never remote messages, bodies or credentials.
        String code = "PERSISTENCE_OR_INTERNAL_ERROR";
        if (error instanceof ScrapingException scrape) {
            code = scrape.getKind().name();
            if (scrape.getRemoteStatus() != null) code += " HTTP_" + scrape.getRemoteStatus();
            if (Set.of("UNAUTHORIZED", "EXTERNAL_API_AUTH_ERROR", "RATE_LIMIT_EXCEEDED",
                    "SCRAPE_BLOCKED", "SCRAPE_TIMEOUT", "PLAYER_NOT_FOUND", "MATCH_NOT_FOUND",
                    "INVALID_EXTERNAL_RESPONSE").contains(scrape.getRemoteCode() == null ? "" : scrape.getRemoteCode())) {
                code += " " + scrape.getRemoteCode();
            }
        }
        runs.error(id, scope + ": " + code);
    }

    private static boolean stopProvider(RuntimeException error) {
        if (!(error instanceof ScrapingException scrape)) return false;
        return scrape.getKind() == ScrapingException.Kind.CONFIGURATION
                || Integer.valueOf(401).equals(scrape.getRemoteStatus())
                || Integer.valueOf(403).equals(scrape.getRemoteStatus())
                || Integer.valueOf(429).equals(scrape.getRemoteStatus())
                || "EXTERNAL_API_AUTH_ERROR".equals(scrape.getRemoteCode())
                || "RATE_LIMIT_EXCEEDED".equals(scrape.getRemoteCode());
    }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new SyncInterrupted();
    }
    private static class SyncInterrupted extends RuntimeException {}
}
