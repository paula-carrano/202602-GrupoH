package com.cbo.players.service;

import com.cbo.players.adapter.ScrapingPort;
import com.cbo.players.adapter.ScrapingException;
import com.cbo.players.adapter.dto.WhoScoredPlayerCandidateDto;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Runs resumable-by-restart bulk matching; already linked players are skipped. */
@Service
public class WhoScoredBulkLinkService {
    public record ReviewItem(Long playerId, String playerName, String currentTeam, String reason,
                             List<WhoScoredPlayerCandidateDto> candidates) {}
    public record JobStatus(String id, String status, int total, int processed, int linked,
                            int noMatch, int needsReview, int errors, List<ReviewItem> reviewItems) {}

    private final PlayerRepository players;
    private final CatalogPersistenceService catalog;
    private final ScrapingPort scraper;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile JobStatus latest;

    public WhoScoredBulkLinkService(PlayerRepository players, CatalogPersistenceService catalog, ScrapingPort scraper) {
        this.players = players;
        this.catalog = catalog;
        this.scraper = scraper;
    }

    public synchronized JobStatus start() {
        if (!running.compareAndSet(false, true)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Ya hay una vinculacion WhoScored en curso");
        }
        try {
            List<Player> pending = players.findAllByActiveTrueAndWhoscoredIdIsNull();
            String id = java.util.UUID.randomUUID().toString();
            latest = new JobStatus(id, "RUNNING", pending.size(), 0, 0, 0, 0, 0, List.of());
            CompletableFuture.runAsync(() -> process(id, pending));
            return latest;
        } catch (RuntimeException error) {
            running.set(false);
            throw error;
        }
    }

    public JobStatus status(String id) {
        JobStatus value = latest;
        if (value == null || !value.id().equals(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Ejecucion de vinculacion inexistente");
        }
        return value;
    }

    private void process(String id, List<Player> pending) {
        int processed = 0, linked = 0, noMatch = 0, needsReview = 0, errors = 0;
        var review = new java.util.ArrayList<ReviewItem>();
        String finalStatus = "COMPLETED";
        try {
            Map<String, List<Player>> byTeam = pending.stream().collect(Collectors.groupingBy(
                    p -> normalize(p.getLeague()) + "|" + normalize(p.getCurrentTeam()), LinkedHashMap::new, Collectors.toList()));
            for (List<Player> teamPlayers : byTeam.values()) {
                Player first = teamPlayers.get(0);
                String country = country(first.getLeague());
                if (country == null) {
                    for (Player player : teamPlayers) {
                        needsReview++;
                        processed++;
                        review.add(new ReviewItem(player.getId(), fullName(player), player.getCurrentTeam(),
                                "UNSUPPORTED_LEAGUE", List.of()));
                    }
                    publishIfDue(id, pending.size(), processed, linked, noMatch, needsReview, errors, review);
                    continue;
                }
                List<WhoScoredPlayerCandidateDto> roster = null;
                try {
                    roster = scraper.searchTeamPlayers(first.getCurrentTeam(), country);
                } catch (RuntimeException error) {
                    errors++;
                    if (isProviderBlocked(error)) {
                        for (Player player : teamPlayers) {
                            needsReview++;
                            processed++;
                            review.add(new ReviewItem(player.getId(), fullName(player), player.getCurrentTeam(),
                                    "SCRAPE_BLOCKED", List.of()));
                        }
                        publishIfDue(id, pending.size(), processed, linked, noMatch, needsReview, errors, review);
                        finalStatus = "BLOCKED";
                        break;
                    }
                    // Roster lookup failed, but each player still gets exactly one
                    // fallback attempt below. Count each player only in that loop.
                    roster = List.of();
                }
                if (finalStatus.equals("BLOCKED")) break;
                if (roster == null) roster = List.of();
                for (Player player : teamPlayers) {
                    String name = fullName(player);
                    List<WhoScoredPlayerCandidateDto> exact = roster.stream()
                            .filter(c -> normalize(c.name()).equals(normalize(name))).toList();
                    try {
                        if (exact.size() == 1) {
                            catalog.link(player.getId(), exact.get(0).whoscoredId());
                            linked++;
                        } else if (exact.isEmpty()) {
                            List<WhoScoredPlayerCandidateDto> search = scraper.searchPlayers(name);
                            List<WhoScoredPlayerCandidateDto> exactSearch = search.stream()
                                    .filter(c -> normalize(c.name()).equals(normalize(name))).toList();
                            if (exactSearch.size() == 1) {
                                catalog.link(player.getId(), exactSearch.get(0).whoscoredId());
                                linked++;
                            } else if (exactSearch.size() > 1) {
                                needsReview++;
                                review.add(new ReviewItem(player.getId(), name, player.getCurrentTeam(),
                                        "MULTIPLE_EXACT_MATCHES", exactSearch));
                            } else {
                                noMatch++;
                            }
                        } else {
                            needsReview++;
                            review.add(new ReviewItem(player.getId(), name, player.getCurrentTeam(),
                                    "MULTIPLE_EXACT_MATCHES", exact));
                        }
                    } catch (RuntimeException error) {
                        errors++;
                        needsReview++;
                        review.add(new ReviewItem(player.getId(), name, player.getCurrentTeam(),
                                isProviderBlocked(error) ? "SCRAPE_BLOCKED" : "LINK_OR_SEARCH_ERROR", exact));
                        if (isProviderBlocked(error)) finalStatus = "BLOCKED";
                    }
                    processed++;
                    publishIfDue(id, pending.size(), processed, linked, noMatch, needsReview, errors, review);
                    if (finalStatus.equals("BLOCKED")) break;
                }
                if (finalStatus.equals("BLOCKED")) break;
            }
            publish(id, pending.size(), processed, linked, noMatch, needsReview, errors, review, finalStatus);
        } finally {
            running.set(false);
        }
    }

    private void publishIfDue(String id, int total, int processed, int linked, int noMatch,
                              int needsReview, int errors, List<ReviewItem> review) {
        if (processed % 10 == 0 || processed == total) {
            publish(id, total, processed, linked, noMatch, needsReview, errors, review, "RUNNING");
        }
    }

    private static boolean isProviderBlocked(RuntimeException error) {
        return error instanceof ScrapingException scrape
                && (Integer.valueOf(403).equals(scrape.getRemoteStatus())
                || Integer.valueOf(429).equals(scrape.getRemoteStatus())
                || "SCRAPE_BLOCKED".equals(scrape.getRemoteCode())
                || "RATE_LIMIT_EXCEEDED".equals(scrape.getRemoteCode()));
    }

    private synchronized void publish(String id, int total, int processed, int linked, int noMatch,
                                      int needsReview, int errors, List<ReviewItem> review, String status) {
        if (latest != null && latest.id().equals(id)) {
            latest = new JobStatus(id, status, total, processed, linked, noMatch, needsReview, errors,
                    List.copyOf(review));
        }
    }

    private static String fullName(Player player) {
        return (player.getFirstName() + " " + (player.getLastName() == null ? "" : player.getLastName())).trim();
    }

    private static String country(String league) {
        if (league == null) return null;
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
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
