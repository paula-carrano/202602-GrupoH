package com.cbo.players.controller;

import com.cbo.players.config.OpenApiConfig;
import com.cbo.players.dto.response.PlayerResponseDto;
import com.cbo.players.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Jugadores", description = "Catálogo de jugadores de fútbol")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    @Operation(
            summary = "Listar jugadores",
            description = "Obtiene el listado completo de jugadores registrados en el catálogo",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de jugadores obtenido exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado (requiere JWT o API Key)")
    })
    public ResponseEntity<List<PlayerResponseDto>> getAllPlayers() {
        List<PlayerResponseDto> players = playerService.getAllPlayers();
        return ResponseEntity.ok(players);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener jugador por ID",
            description = "Obtiene el detalle de un jugador específico a través de su identificador numérico",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jugador encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado (requiere JWT o API Key)"),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    })
    public ResponseEntity<PlayerResponseDto> getPlayerById(
            @Parameter(description = "Identificador único del jugador", example = "1")
            @PathVariable Long id) {
        PlayerResponseDto player = playerService.getPlayerById(id);
        return ResponseEntity.ok(player);
    }
}
