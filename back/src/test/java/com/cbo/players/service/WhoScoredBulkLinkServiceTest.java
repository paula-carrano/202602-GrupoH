package com.cbo.players.service;

import com.cbo.players.adapter.*;
import com.cbo.players.adapter.dto.WhoScoredPlayerCandidateDto;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhoScoredBulkLinkServiceTest {
    PlayerRepository players = mock(PlayerRepository.class);
    CatalogPersistenceService catalog = mock(CatalogPersistenceService.class);
    ScrapingPort scraper = mock(ScrapingPort.class);
    WhoScoredBulkLinkService service = new WhoScoredBulkLinkService(players, catalog, scraper, Runnable::run);

    private Player player(long id, String name, String league) {
        Player p = new Player();
        p.setId(id);
        p.setFirstName(name);
        p.setLastName(null);
        p.setCurrentTeam("Team");
        p.setLeague(league);
        return p;
    }

    private WhoScoredPlayerCandidateDto candidate(long id, String name) {
        return new WhoScoredPlayerCandidateDto(id, name, "https://www.whoscored.com/players/" + id);
    }

    private WhoScoredBulkLinkService.JobStatus run(Player... pending) {
        when(players.findAllByActiveTrueAndWhoscoredIdIsNull()).thenReturn(List.of(pending));
        return service.status(service.start().id());
    }

    @ParameterizedTest
    @CsvSource({ "PL,England", "BL1,Germany", "PD,Spain", "SA,Italy", "FL1,France" })
    void linksNormalizedExactRosterMatch(String league, String country) {
        when(scraper.searchTeamPlayers("Team", country)).thenReturn(List.of(candidate(10, "Jose Perez")));
        var status = run(player(1, "José Pérez", league));
        assertEquals("COMPLETED", status.status());
        assertEquals(1, status.linked());
        assertEquals(1, status.processed());
        verify(catalog).link(1L, 10L);
        verify(scraper, never()).searchPlayers(anyString());
    }

    @Test
    void fallbackCountsAmbiguousAndMissingMatchesSeparately() {
        when(scraper.searchTeamPlayers(anyString(), anyString())).thenReturn(null);
        when(scraper.searchPlayers("One")).thenReturn(List.of(candidate(10, "One")));
        when(scraper.searchPlayers("Two")).thenReturn(List.of(candidate(20, "Two"), candidate(21, "Two")));
        when(scraper.searchPlayers("Three")).thenReturn(List.of(candidate(30, "Other")));
        var status = run(player(1, "One", "PL"), player(2, "Two", "PL"), player(3, "Three", "PL"));
        assertEquals(3, status.processed());
        assertEquals(1, status.linked());
        assertEquals(1, status.noMatch());
        assertEquals(1, status.needsReview());
        assertEquals("MULTIPLE_EXACT_MATCHES", status.reviewItems().get(0).reason());
        verify(catalog).link(1L, 10L);
    }

    @Test
    void ambiguousRosterDoesNotGuessOrSearchAgain() {
        when(scraper.searchTeamPlayers(anyString(), anyString()))
                .thenReturn(List.of(candidate(10, "One"), candidate(11, "One")));
        var status = run(player(1, "One", "PL"));
        assertEquals(1, status.needsReview());
        verifyNoInteractions(catalog);
        verify(scraper, never()).searchPlayers(anyString());
    }

    @Test
    void unsupportedLeaguesAreReviewableWithoutCallingProvider() {
        var status = run(player(1, "One", "UNKNOWN"), player(2, "Two", null));
        assertEquals(2, status.processed());
        assertEquals(2, status.needsReview());
        assertTrue(status.reviewItems().stream().allMatch(r -> r.reason().equals("UNSUPPORTED_LEAGUE")));
        verifyNoInteractions(scraper, catalog);
    }

    @Test
    void rosterFailureStillAttemptsEachPlayerExactlyOnce() {
        when(scraper.searchTeamPlayers(anyString(), anyString())).thenThrow(new IllegalStateException("offline"));
        var status = run(player(1, "One", "PL"));
        assertEquals(1, status.errors());
        assertEquals(1, status.processed());
        assertEquals(1, status.noMatch());
        verify(scraper).searchPlayers("One");
    }

    @ParameterizedTest
    @CsvSource(value = { "403,NULL", "429,NULL", "502,SCRAPE_BLOCKED", "502,RATE_LIMIT_EXCEEDED" }, nullValues = "NULL")
    void providerBlockStopsRemainingTeams(int http, String code) {
        when(scraper.searchTeamPlayers("Team", "England")).thenThrow(
                new ScrapingException(ScrapingException.Kind.REMOTE_HTTP, "blocked", http, code));
        var status = run(player(1, "One", "PL"), player(2, "Two", "PL"), player(3, "Three", "PD"));
        assertEquals("BLOCKED", status.status());
        assertEquals(2, status.processed());
        assertEquals(2, status.needsReview());
        assertEquals(1, status.errors());
        verify(scraper, never()).searchTeamPlayers("Team", "Spain");
        verify(scraper, never()).searchPlayers(anyString());
    }

    @Test
    void fallbackBlockStopsPlayersWithoutRetryingAndAllowsNewJob() {
        when(scraper.searchPlayers("One")).thenThrow(new ScrapingException(
                ScrapingException.Kind.REMOTE_HTTP, "blocked", 429, null));
        var status = run(player(1, "One", "PL"), player(2, "Two", "PL"));
        assertEquals("BLOCKED", status.status());
        assertEquals(1, status.processed());
        verify(scraper, never()).searchPlayers("Two");
        assertEquals("COMPLETED", run().status());
    }

    @Test
    void persistenceFailureDoesNotAbortTheRestOfTheJob() {
        when(scraper.searchTeamPlayers(anyString(), anyString())).thenReturn(List.of(candidate(10, "One")));
        when(catalog.link(1L, 10L)).thenThrow(new IllegalStateException("conflict"));
        var status = run(player(1, "One", "PL"), player(2, "Two", "PL"));
        assertEquals("COMPLETED", status.status());
        assertEquals(2, status.processed());
        assertEquals("LINK_OR_SEARCH_ERROR", status.reviewItems().get(0).reason());
    }

    @Test
    void queuedWorkRejectsOverlapAndPublishesImmutableSnapshots() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        service = new WhoScoredBulkLinkService(players, catalog, scraper, task::set);
        when(players.findAllByActiveTrueAndWhoscoredIdIsNull())
                .thenReturn(List.of(player(1, "One", "unknown")));

        var initial = service.start();

        assertEquals(
                409,
                assertThrows(ResponseStatusException.class, service::start)
                        .getStatusCode().value());

        task.get().run();

        var completed = service.status(initial.id());

        assertEquals(0, initial.processed());
        assertEquals(1, completed.processed());

        assertThrows(
                UnsupportedOperationException.class,
                () -> completed.reviewItems().clear());

        var candidates = completed.reviewItems().get(0).candidates();

        assertThrows(
                UnsupportedOperationException.class,
                candidates::clear);
    }

    @Test
    void executorRejectionAndRepositoryFailureReleaseBusyFlag() {
        Executor executor = mock(Executor.class);
        service = new WhoScoredBulkLinkService(players, catalog, scraper, executor);
        doThrow(new RejectedExecutionException()).doNothing().when(executor).execute(any());
        assertThrows(RejectedExecutionException.class, service::start);
        assertDoesNotThrow(service::start);
        service = new WhoScoredBulkLinkService(players, catalog, scraper, Runnable::run);
        when(players.findAllByActiveTrueAndWhoscoredIdIsNull()).thenThrow(new IllegalStateException())
                .thenReturn(List.of());
        assertThrows(IllegalStateException.class, service::start);
        assertDoesNotThrow(service::start);
    }

    @Test
    void unknownIdIsNotFound() {
        assertEquals(404,
                assertThrows(ResponseStatusException.class, () -> service.status("missing")).getStatusCode().value());
        run();
        assertEquals(404,
                assertThrows(ResponseStatusException.class, () -> service.status("old")).getStatusCode().value());
    }
}
