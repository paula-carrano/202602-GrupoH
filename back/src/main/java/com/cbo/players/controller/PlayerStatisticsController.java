package com.cbo.players.controller;
import com.cbo.players.dto.response.StoredPlayerStatsDto;
import com.cbo.players.service.CatalogPersistenceService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/players")
public class PlayerStatisticsController {
    private final CatalogPersistenceService catalog;
    public PlayerStatisticsController(CatalogPersistenceService catalog) { this.catalog = catalog; }
    @GetMapping("/{id}/stats")
    public StoredPlayerStatsDto stats(@PathVariable Long id) { return catalog.statistics(id); }
}
