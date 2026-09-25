package com.cbo.players.controller;
import com.cbo.players.dto.response.CatalogSyncRunDto;
import com.cbo.players.model.CatalogSyncRun;
import com.cbo.players.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin")
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
    public CatalogAdminController(CatalogSyncService sync, CatalogRunService runs, CatalogPersistenceService catalog) {
        this.sync = sync; this.runs = runs; this.catalog = catalog;
    }
    public record StartedRun(Long id) {}
    public record WhoScoredLink(@com.fasterxml.jackson.annotation.JsonProperty(required = true) @Positive Long whoscoredId) {}
    @PostMapping("/catalog-sync")
    public ResponseEntity<StartedRun> start() {
        Long id = sync.start(CatalogSyncRun.Origin.MANUAL);
        return ResponseEntity.accepted().location(URI.create("/api/v1/admin/catalog-sync/" + id)).body(new StartedRun(id));
    }
    @GetMapping("/catalog-sync/{id}")
    public CatalogSyncRunDto get(@PathVariable Long id) { return runs.get(id); }
    @PutMapping("/players/{id}/whoscored")
    public CatalogPersistenceService.LinkResult link(@PathVariable Long id, @Valid @RequestBody WhoScoredLink request) {
        return catalog.link(id, request.whoscoredId());
    }
}
