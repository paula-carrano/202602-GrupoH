package com.cbo.players.service;

import com.cbo.players.adapter.*;
import com.cbo.players.adapter.dto.*;
import com.cbo.players.model.*;
import com.cbo.players.repository.*;
import com.cbo.players.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.scheduling.support.CronExpression;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.catalog-sync.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIntegrationTest {
    @MockBean ScrapingPort scraper;
    @Autowired CatalogPersistenceService catalog;
    @Autowired CatalogSyncService sync;
    @Autowired CatalogRunService runs;
    @Autowired PlayerRepository players;
    @Autowired PlayerStatisticsRepository stats;
    @Autowired CatalogSyncRunRepository runRepo;
    @Autowired MockMvc mvc;

    private static final PlayerStatsDto METRICS = new PlayerStatsDto(1,2,3,4,5,6,7.65,800,1,0);
    private static TeamSquadDto squad(long teamId, String teamName, long playerId) {
        return new TeamSquadDto(new CatalogTeamDto(teamId, teamName), List.of(
            new SquadPlayerDto(playerId, "Nombre Completo", null, null, LocalDate.of(2000,1,1), null, "Unknown")));
    }
    @BeforeEach
    void setup() {
        stats.deleteAll(); players.deleteAll(); runRepo.deleteAll();
        when(scraper.getCompetitionTeams(anyString())).thenReturn(List.of());
    }
    private Long importedPlayer() {
        catalog.importSquad("PL", squad(10, "Team", 100));
        return players.findByFootballDataId(100L).orElseThrow().getId();
    }
    private void completed(Long id) {
        await().atMost(Duration.ofSeconds(10)).until(() -> runs.get(id).status() != CatalogSyncRun.Status.RUNNING);
    }

    @Test
    void importIsIdempotentAndPreservesLinksAcrossTransfers() {
        Long id = importedPlayer();
        catalog.link(id, 123L);
        var result = catalog.importSquad("PD", squad(20, "New Team", 100));
        assertEquals(0, result.created()); assertEquals(1, result.updated());
        assertEquals(1, players.count());
        var player = players.findById(id).orElseThrow();
        assertEquals(123L, player.getWhoscoredId());
        assertEquals("New Team", player.getCurrentTeam());
        assertEquals("PD", player.getLeague());
        assertEquals("Nombre Completo", player.getFirstName());
        assertEquals("", player.getLastName());
        assertNull(player.getPosition()); assertNull(player.getNationality());
        assertNotNull(player.getLastCatalogSyncAt());
    }

    @Test
    void failedPlantelRollsBackAndEmptySquadDoesNotRemovePlayers() {
        importedPlayer();
        var invalid = new TeamSquadDto(new CatalogTeamDto(10L, "X".repeat(100)), squad(10,"Team",101).players());
        assertThrows(RuntimeException.class, () -> catalog.importSquad("PL", invalid));
        assertEquals(1, players.count());
        catalog.importSquad("PL", new TeamSquadDto(new CatalogTeamDto(10L,"Team"), List.of()));
        assertEquals(1, players.count());
    }

    @Test
    void uniqueLinkAndStaleResponseProtection() {
        Long first = importedPlayer();
        catalog.importSquad("PL", squad(10,"Team",101));
        Long second = players.findByFootballDataId(101L).orElseThrow().getId();
        catalog.link(first,123L);
        assertThrows(ApiException.class, () -> catalog.link(second,123L));
        var oldLink = new CatalogPersistenceService.LinkedPlayer(first,123L);
        assertTrue(catalog.saveStatistics(oldLink,METRICS));
        assertEquals(7.65, catalog.statistics(first).metrics().rating());
        catalog.link(first,123L); // same association must retain the reading
        assertEquals(1, stats.count());
        catalog.link(first,456L);
        assertEquals(0,stats.count());
        assertFalse(catalog.saveStatistics(oldLink,METRICS));
        catalog.saveStatistics(new CatalogPersistenceService.LinkedPlayer(first,456L),METRICS);
        catalog.link(first,null);
        assertEquals(0,stats.count());
        assertNull(players.findById(first).orElseThrow().getWhoscoredId());
    }

    @Test
    void jobsImportFiveLeaguesAndSurviveAnIsolatedFailure() {
        when(scraper.getCompetitionTeams("PL")).thenReturn(List.of(new CatalogTeamDto(10L,"Team"),new CatalogTeamDto(11L,"Other")));
        when(scraper.getTeamSquad(10)).thenThrow(new ScrapingException(ScrapingException.Kind.CONNECTION,"private details"));
        when(scraper.getTeamSquad(11)).thenReturn(squad(11,"Other",100));
        Long id = sync.start(CatalogSyncRun.Origin.MANUAL);
        completed(id);
        var run = runs.get(id);
        assertEquals(CatalogSyncRun.Status.PARTIAL,run.status());
        assertEquals(1,run.playersCreated());
        assertEquals(1,run.errorCount());
        assertFalse(run.errors().toString().contains("private details"));
        for (String league : List.of("PL","BL1","PD","SA","FL1")) verify(scraper).getCompetitionTeams(league);
    }

    @Test
    void failedStatisticsPreserveLastSuccessfulData() {
        Long playerId = importedPlayer(); catalog.link(playerId,123L);
        catalog.saveStatistics(new CatalogPersistenceService.LinkedPlayer(playerId,123L),METRICS);
        var timestamp = catalog.statistics(playerId).lastSuccessfulSyncAt();
        when(scraper.getPlayerStats(123)).thenThrow(new ScrapingException(ScrapingException.Kind.TIMEOUT,"timeout"));
        Long id = sync.start(CatalogSyncRun.Origin.MANUAL); completed(id);
        assertEquals(timestamp,catalog.statistics(playerId).lastSuccessfulSyncAt());
        assertEquals(METRICS,catalog.statistics(playerId).metrics());
    }

    @Test
    void providerAuthenticationStopsCatalogPhaseButStillUpdatesLinkedStatistics() {
        Long playerId = importedPlayer(); catalog.link(playerId,123L);
        when(scraper.getCompetitionTeams("PL")).thenThrow(new ScrapingException(
                ScrapingException.Kind.REMOTE_HTTP,"auth",502,"EXTERNAL_API_AUTH_ERROR"));
        when(scraper.getPlayerStats(123)).thenReturn(METRICS);
        Long id = sync.start(CatalogSyncRun.Origin.MANUAL); completed(id);
        verify(scraper,never()).getCompetitionTeams("BL1");
        assertEquals(1,runs.get(id).statisticsUpdated());
        assertEquals(CatalogSyncRun.Status.PARTIAL,runs.get(id).status());
    }

    @Test
    void backgroundJobRejectsOverlapAndRecoversInterruptedRuns() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(scraper.getCompetitionTeams("PL")).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return List.of();
        });
        Long id = sync.start(CatalogSyncRun.Origin.MANUAL);
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            mvc.perform(post("/api/v1/admin/catalog-sync").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isConflict());
            assertEquals(CatalogSyncRun.Status.RUNNING,runs.get(id).status());
        } finally { release.countDown(); }
        completed(id);
        assertEquals(CatalogSyncRun.Status.SUCCESS,runs.get(id).status());
        Long stale = runs.create(CatalogSyncRun.Origin.SCHEDULED);
        runs.recoverInterrupted();
        assertEquals(CatalogSyncRun.Status.INTERRUPTED,runs.get(stale).status());
    }

    @Test
    void adminEndpointsRequireRoleAndValidateLinks() throws Exception {
        Long id = importedPlayer();
        mvc.perform(post("/api/v1/admin/catalog-sync").with(user("regular").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/catalog-sync/1").with(user("regular").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/players/"+id+"/whoscored").with(user("regular").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"whoscoredId\":123}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/players/"+id+"/whoscored").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/players/"+id+"/whoscored").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"whoscoredId\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/players/"+id+"/whoscored").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"whoscoredId\":123}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.whoscoredId").value(123));
        mvc.perform(get("/api/v1/players/"+id+"/stats").with(user("regular").roles("USER")))
                .andExpect(status().isNotFound());
        catalog.saveStatistics(new CatalogPersistenceService.LinkedPlayer(id,123L),METRICS);
        mvc.perform(get("/api/v1/players/"+id+"/stats").with(user("regular").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics.rating").value(7.65));
        mvc.perform(get("/api/v1/admin/catalog-sync/999").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualEndpointReturnsAcceptedAndCanBePolled() throws Exception {
        String response = mvc.perform(post("/api/v1/admin/catalog-sync").with(user("admin").roles("ADMIN")))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        Long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
        completed(id);
        mvc.perform(get("/api/v1/admin/catalog-sync/"+id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void weeklyCronUsesArgentinaTime() throws Exception {
        var annotation = CatalogScheduler.class.getMethod("synchronize")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertEquals("${app.catalog-sync.cron:0 0 15 * * MON}",annotation.cron());
        assertEquals("${app.catalog-sync.zone:America/Argentina/Buenos_Aires}",annotation.zone());
        var zone = ZoneId.of("America/Argentina/Buenos_Aires");
        var next = CronExpression.parse("0 0 15 * * MON").next(ZonedDateTime.of(2026,9,25,15,0,0,0,zone));
        assertEquals(Instant.parse("2026-09-28T18:00:00Z"),next.toInstant());
    }
}
