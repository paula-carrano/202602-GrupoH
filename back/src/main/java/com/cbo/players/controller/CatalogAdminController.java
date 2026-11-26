package com.cbo.players.controller;

import com.cbo.players.dto.response.CatalogSyncRunDto;
import com.cbo.players.dto.response.WhoScoredCandidateResponseDto;
import com.cbo.players.model.CatalogSyncRun;
import com.cbo.players.service.CatalogPersistenceService;
import com.cbo.players.service.CatalogRunService;
import com.cbo.players.service.CatalogSyncService;
import com.cbo.players.service.WhoScoredBulkLinkService;
import com.cbo.players.service.WhoScoredCandidateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class CatalogAdminController {

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<org.springframework.http.ProblemDetail> conflict() {
        var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.CONFLICT, "El identificador externo ya esta vinculado.");
        problem.setProperty("errorCode", "WHOSCORED_ID_CONFLICT");
        return ResponseEntity.status(409).body(problem);
    }

    private final CatalogSyncService sync;
    private final CatalogRunService runs;
    private final CatalogPersistenceService catalog;
    private final WhoScoredCandidateService candidateService;
    private final WhoScoredBulkLinkService bulkLink;

    public CatalogAdminController(
            CatalogSyncService sync,
            CatalogRunService runs,
            CatalogPersistenceService catalog,
            WhoScoredCandidateService candidateService,
            WhoScoredBulkLinkService bulkLink) {
        this.sync = sync;
        this.runs = runs;
        this.catalog = catalog;
        this.candidateService = candidateService;
        this.bulkLink = bulkLink;
    }

    public record StartedRun(Long id) {}
    public record WhoScoredLink(@com.fasterxml.jackson.annotation.JsonProperty(required = true) @Positive Long whoscoredId) {}

    @PostMapping("/catalog-sync")
    public ResponseEntity<StartedRun> start() {
        Long id = sync.start(CatalogSyncRun.Origin.MANUAL);
        return ResponseEntity.accepted().location(URI.create("/api/admin/catalog-sync/" + id)).body(new StartedRun(id));
    }

    @GetMapping("/catalog-sync/{id}")
    public CatalogSyncRunDto get(@PathVariable Long id) {
        return runs.get(id);
    }

    @GetMapping("/players/{id}/whoscored/candidates")
    public List<WhoScoredCandidateResponseDto> candidates(@PathVariable Long id) {
        return candidateService.findCandidates(id);
    }

    @PutMapping("/players/{id}/whoscored")
    public CatalogPersistenceService.LinkResult link(@PathVariable Long id, @Valid @RequestBody WhoScoredLink request) {
        return catalog.link(id, request.whoscoredId());
    }

    @PostMapping("/whoscored/bulk-link")
    public WhoScoredBulkLinkService.JobStatus startBulkLink() {
        return bulkLink.start();
    }

    @GetMapping("/whoscored/bulk-link/{jobId}")
    public WhoScoredBulkLinkService.JobStatus bulkLinkStatus(@PathVariable String jobId) {
        return bulkLink.status(jobId);
    }
}
