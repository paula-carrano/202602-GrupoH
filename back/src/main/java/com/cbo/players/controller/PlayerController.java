package com.cbo.players.controller;

import com.cbo.players.config.OpenApiConfig;
import com.cbo.players.dto.response.PlayerDetailResponseDto;
import com.cbo.players.dto.response.PlayerRankingResponseDto;
import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.service.PlayerQuoteService;
import com.cbo.players.service.PlayerService;
import com.cbo.players.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Jugadores", description = "Catálogo de jugadores de fútbol, cotizaciones y ranking de mercado")
public class PlayerController {

    private final PlayerService playerService;
    private final PlayerQuoteService playerQuoteService;
    private final RankingService rankingService;

    public PlayerController(PlayerService playerService,
                            PlayerQuoteService playerQuoteService,
                            RankingService rankingService) {
        this.playerService = playerService;
        this.playerQuoteService = playerQuoteService;
        this.rankingService = rankingService;
    }

    @GetMapping
    @Operation(summary = "Listar jugadores", description = "Obtiene el listado completo de jugadores registrados en el catálogo", security = {
            @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
    })
    @ApiResponse(responseCode = "200", description = "Listado de jugadores obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado (requiere JWT o API Key)")
    public ResponseEntity<List<PlayerResponseDto>> getAllPlayers() {
        List<PlayerResponseDto> players = playerService.getAllPlayers();
        return ResponseEntity.ok(players);
    }

    @GetMapping("/ranking")
    @Operation(summary = "Ranking de jugadores por cotización", description = "Devuelve el listado ordenado descendentemente por cotización bajo la estrategia activa, acelerado con caché Redis.", security = {
            @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
    })
    @ApiResponse(responseCode = "200", description = "Ranking de jugadores obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public ResponseEntity<PlayerRankingResponseDto> getRanking() {
        return ResponseEntity.ok(rankingService.getRanking());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener jugador por ID", description = "Obtiene el detalle de un jugador específico a través de su identificador numérico", security = {
            @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
    })
    @ApiResponse(responseCode = "200", description = "Jugador encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado (requiere JWT o API Key)")
    @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    public ResponseEntity<PlayerDetailResponseDto> getPlayerById(
            @Parameter(description = "Identificador único del jugador", example = "1") @PathVariable Long id) {
        PlayerDetailResponseDto player = playerService.getPlayerById(id);
        return ResponseEntity.ok(player);
    }

    @GetMapping("/{id}/quotes")
    @Operation(summary = "Consultar cotizaciones históricas o por fecha", description = "Devuelve el historial cronológico paginado de cotizaciones o la cotización vigente para una fecha específica si se provee el parámetro 'date'.", security = {
            @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
    })
    @ApiResponse(responseCode = "200", description = "Cotización o listado paginado obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    public ResponseEntity<?> getPlayerQuotes(
            @Parameter(description = "Identificador único del jugador", example = "45") @PathVariable Long id,
            @Parameter(description = "Fecha específica (YYYY-MM-DD) para consultar la cotización vigente a esa fecha", example = "2026-10-20")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Número de página (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "20") int size) {
        if (date != null) {
            return ResponseEntity.ok(playerQuoteService.getQuoteByDate(id, date));
        }
        return ResponseEntity.ok(playerQuoteService.getPaginatedQuotes(id, page, size));
    }
}
