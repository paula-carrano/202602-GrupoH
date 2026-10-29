package com.cbo.players.controller;

import com.cbo.players.dto.response.StrategyResponseDto;
import com.cbo.players.model.ValuationStrategy;
import com.cbo.players.security.UserPrincipal;
import com.cbo.players.service.ValuationStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/strategies")
@Tag(name = "Admin Strategies", description = "Control administrativo centralizado de estrategias de valoración")
@SecurityRequirement(name = "bearerAuth")
public class AdminStrategyController {

    private final ValuationStrategyService valuationStrategyService;

    public AdminStrategyController(ValuationStrategyService valuationStrategyService) {
        this.valuationStrategyService = valuationStrategyService;
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPERUSER')")
    @Operation(summary = "Activar estrategia de valoración", description = "Activa una estrategia de valoración para el recálculo global de cotizaciones. Exclusivo para superusuario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estrategia activada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado: permisos exclusivos de superusuario requeridos"),
            @ApiResponse(responseCode = "404", description = "Estrategia no encontrada")
    })
    public ResponseEntity<StrategyResponseDto> activateStrategy(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long actorId = principal != null ? principal.getId() : 1L;
        ValuationStrategy activated = valuationStrategyService.activateStrategy(id, actorId);
        return ResponseEntity.ok(StrategyResponseDto.from(activated));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERUSER')")
    @Operation(summary = "Listar todas las estrategias", description = "Lista todas las estrategias de valoración registradas en el sistema.")
    public ResponseEntity<List<StrategyResponseDto>> getAllStrategies() {
        List<StrategyResponseDto> dtos = valuationStrategyService.getAllStrategies().stream()
                .map(StrategyResponseDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERUSER')")
    @Operation(summary = "Obtener estrategia por ID", description = "Devuelve el detalle de una estrategia de valoración por su identificador.")
    public ResponseEntity<StrategyResponseDto> getStrategyById(@PathVariable Long id) {
        ValuationStrategy strategy = valuationStrategyService.getStrategyById(id);
        return ResponseEntity.ok(StrategyResponseDto.from(strategy));
    }
}
