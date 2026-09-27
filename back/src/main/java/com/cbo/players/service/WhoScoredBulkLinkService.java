package com.cbo.players.service;

import com.cbo.players.adapter.ScrapingPort;
import com.cbo.players.adapter.ScrapingException;
import com.cbo.players.adapter.dto.WhoScoredPlayerCandidateDto;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Runs resumable-by-restart bulk matching; already linked players are skipped.
 */
@Service
public class WhoScoredBulkLinkService {
    private static final String BLOCKED = "BLOCKED";
    private static final String SCRAPE_BLOCKED = "SCRAPE_BLOCKED";

    public record ReviewItem(Long playerId, String playerName, String currentTeam, String reason,
            List<WhoScoredPlayerCandidateDto> candidates) {
        public ReviewItem {
            candidates = List.copyOf(candidates);
        }
    }

    public record JobStatus(String id, String status, int total, int processed, int linked,
            int noMatch, int needsReview, int errors, List<ReviewItem> reviewItems) {
        public JobStatus {
            reviewItems = List.copyOf(reviewItems);
        }
    }

    private final PlayerRepository players;
    private final CatalogPersistenceService catalog;
    private final ScrapingPort scraper;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<JobStatus> latest = new AtomicReference<>();

    public WhoScoredBulkLinkService(PlayerRepository players, CatalogPersistenceService catalog,
            ScrapingPort scraper, @Qualifier("bulkLinkExecutor") Executor executor) {
        this.players = players;
        this.catalog = catalog;
        this.scraper = scraper;
        this.executor = executor;
    }

    public synchronized JobStatus start() {
        if (!running.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una vinculacion WhoScored en curso");
        }
        JobStatus previous = latest.get();
        try {
            List<Player> pending = players.findAllByActiveTrueAndWhoscoredIdIsNull();
            Progress progress = new Progress(UUID.randomUUID().toString(), pending.size());
            JobStatus initial = progress.snapshot();
            latest.set(initial);
            executor.execute(() -> process(progress, pending));
            return initial;
        } catch (RuntimeException error) {
            latest.set(previous);
            running.set(false);
            throw error;
        }
    }

    public JobStatus status(String id) {
        JobStatus value = latest.get();
        if (value == null || !value.id().equals(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejecucion de vinculacion inexistente");
        }
        return value;
    }

    // Mutable counters are confined to the worker; readers only see immutable
    // snapshots.
    private static class Progress {
        final String id;
        final int total;
        int processed;
        int linked;
        int noMatch;
        int errors;
        String status = "RUNNING";
        final List<ReviewItem> review = new ArrayList<>();

        Progress(String id, int total) {
            this.id = id;
            this.total = total;
        }

        void addReviewItem(Player player, String reason, List<WhoScoredPlayerCandidateDto> candidates) {
            review.add(new ReviewItem(player.getId(), fullName(player), player.getCurrentTeam(), reason, candidates));
        }

        JobStatus snapshot() {
            return new JobStatus(id, status, total, processed, linked, noMatch, review.size(), errors, review);
        }
    }

    private void process(Progress progress, List<Player> pending) {
        try {
            Map<String, List<Player>> byTeam = pending.stream().collect(Collectors.groupingBy(
                    p -> normalize(p.getLeague()) + "|" + normalize(p.getCurrentTeam()),
                    LinkedHashMap::new, Collectors.toList()));
            for (List<Player> team : byTeam.values()) {
                processTeam(progress, team);
                if (BLOCKED.equals(progress.status))
                    break;
            }
            if (!BLOCKED.equals(progress.status))
                progress.status = "COMPLETED";
        } catch (RuntimeException error) {
            progress.errors++;
            progress.status = "FAILED";
        } finally {
            latest.set(progress.snapshot());
            running.set(false);
        }
    }

    private void processTeam(Progress progress, List<Player> team) {
        Player first = team.get(0);
        String country = country(first.getLeague());
        if (country == null) {
            reviewTeam(progress, team, "UNSUPPORTED_LEAGUE");
            return;
        }
        List<WhoScoredPlayerCandidateDto> roster;
        try {
            roster = scraper.searchTeamPlayers(first.getCurrentTeam(), country);
        } catch (RuntimeException error) {
            progress.errors++;
            if (isProviderBlocked(error)) {
                progress.status = BLOCKED;
                reviewTeam(progress, team, SCRAPE_BLOCKED);
                return;
            }
            roster = List.of();
        }
        if (roster == null)
            roster = List.of();
        for (Player player : team) {
            processPlayer(progress, player, roster);
            progress.processed++;
            publishIfDue(progress);
            if (BLOCKED.equals(progress.status))
                return;
        }
    }

    private void reviewTeam(Progress progress, List<Player> team, String reason) {
        for (Player player : team) {
            progress.addReviewItem(player, reason, List.of());
            progress.processed++;
        }
        publishIfDue(progress);
    }

    private void processPlayer(Progress progress, Player player, List<WhoScoredPlayerCandidateDto> roster) {
        List<WhoScoredPlayerCandidateDto> exact = exactMatches(roster, player);
        try {
            if (exact.isEmpty())
                exact = exactMatches(scraper.searchPlayers(fullName(player)), player);
            if (exact.size() == 1) {
                catalog.link(player.getId(), exact.get(0).whoscoredId());
                progress.linked++;
            } else if (exact.isEmpty()) {
                progress.noMatch++;
            } else {
                progress.review(player, "MULTIPLE_EXACT_MATCHES", exact);
            }
        } catch (RuntimeException error) {
            progress.errors++;
            boolean blocked = isProviderBlocked(error);
            progress.review(player, blocked ? SCRAPE_BLOCKED : "LINK_OR_SEARCH_ERROR", exact);
            if (blocked)
                progress.status = BLOCKED;
        }
    }

    private static List<WhoScoredPlayerCandidateDto> exactMatches(List<WhoScoredPlayerCandidateDto> candidates,
            Player player) {
        String expected = normalize(fullName(player));
        return candidates.stream().filter(c -> normalize(c.name()).equals(expected)).toList();
    }

    private void publishIfDue(Progress progress) {
        if (progress.processed % 10 == 0 || progress.processed == progress.total)
            latest.set(progress.snapshot());
    }

    private static boolean isProviderBlocked(RuntimeException error) {
        return error instanceof ScrapingException scrape
                && (Integer.valueOf(403).equals(scrape.getRemoteStatus())
                        || Integer.valueOf(429).equals(scrape.getRemoteStatus())
                        || SCRAPE_BLOCKED.equals(scrape.getRemoteCode())
                        || "RATE_LIMIT_EXCEEDED".equals(scrape.getRemoteCode()));
    }

    private static String fullName(Player player) {
        return (player.getFirstName() + " " + (player.getLastName() == null ? "" : player.getLastName())).trim();
    }

    private static String country(String league) {
        if (league == null)
            return null;
        return switch (league.toUpperCase(Locale.ROOT)) {
            case "PL" -> "England";
            case "BL1" -> "Germany";
            case "PD" -> "Spain";
            case "SA" -> "Italy";
            case "FL1" -> "France";
            default -> null;
        };
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
    }
}
