package com.cbo.players.controller;

import com.cbo.players.adapter.ScrapingPort;
import com.cbo.players.adapter.dto.WhoScoredPlayerCandidateDto;
import com.cbo.players.dto.response.WhoScoredCandidateResponseDto;
import com.cbo.players.exception.ResourceNotFoundException;
import com.cbo.players.model.Player;
import com.cbo.players.repository.PlayerRepository;
import com.cbo.players.service.CatalogPersistenceService;
import com.cbo.players.service.CatalogRunService;
import com.cbo.players.service.CatalogSyncService;
import com.cbo.players.service.WhoScoredBulkLinkService;
import com.cbo.players.service.WhoScoredCandidateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogAdminControllerTest {
    PlayerRepository players = mock(PlayerRepository.class);
    ScrapingPort scraper = mock(ScrapingPort.class);
    WhoScoredCandidateService candidateService = new WhoScoredCandidateService(players, scraper);
    WhoScoredBulkLinkService bulk = mock(WhoScoredBulkLinkService.class);
    CatalogAdminController controller = new CatalogAdminController(
            mock(CatalogSyncService.class),
            mock(CatalogRunService.class),
            mock(CatalogPersistenceService.class),
            candidateService,
            bulk);

    @Test
    void findsCandidatesAndLimitsSearchQuery() {
        Player player = new Player();
        player.setFirstName("Alex");
        player.setLastName("Doe");
        when(players.findById(1L)).thenReturn(Optional.of(player));
        var adapterResult = List.of(new WhoScoredPlayerCandidateDto(10L, "Alex Doe", "https://www.whoscored.com/players/10"));
        when(scraper.searchPlayers("Alex Doe")).thenReturn(adapterResult);

        var expected = List.of(new WhoScoredCandidateResponseDto(10L, "Alex Doe", "https://www.whoscored.com/players/10"));
        assertEquals(expected, controller.candidates(1L));

        player.setFirstName("x".repeat(130));
        controller.candidates(1L);
        verify(scraper).searchPlayers("x".repeat(120));
        assertThrows(ResourceNotFoundException.class, () -> controller.candidates(2L));
    }

    @Test
    void exposesBulkStartAndStatusAndMapsUniqueConstraintConflicts() {
        var status = new WhoScoredBulkLinkService.JobStatus("job", "RUNNING", 1, 0, 0, 0, 0, 0, List.of());
        when(bulk.start()).thenReturn(status);
        when(bulk.status("job")).thenReturn(status);
        assertEquals(status, controller.startBulkLink());
        assertEquals(status, controller.bulkLinkStatus("job"));
        var conflict = controller.conflict();
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("WHOSCORED_ID_CONFLICT", conflict.getBody().getProperties().get("errorCode"));
    }
}
