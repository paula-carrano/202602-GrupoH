package com.cbo.players.controller;

import com.cbo.players.config.OpenApiConfig;
import com.cbo.players.dto.response.AdvancedMetricsResponseDto;
import com.cbo.players.service.AdvancedMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
@Tag(name = "Métricas Avanzadas", description = "Observabilidad consolidada de negocio, valoración, alertas y rendimiento de caché")
public class AdvancedMetricsController {

    private final AdvancedMetricsService advancedMetricsService;

    public AdvancedMetricsController(AdvancedMetricsService advancedMetricsService) {
        this.advancedMetricsService = advancedMetricsService;
    }

    @GetMapping("/advanced")
    @Operation(
            summary = "Consultar métricas avanzadas consolidadas",
            description = "Retorna métricas consolidadas de mercado, valoración, alertas y rendimiento de caché. Exclusivo para el superusuario.",
            security = { @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH) }
    )
    @ApiResponse(responseCode = "200", description = "Métricas avanzadas generadas exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Acceso denegado (requiere rol SUPERUSER)")
    public ResponseEntity<AdvancedMetricsResponseDto> getAdvancedMetrics() {
        return ResponseEntity.ok(advancedMetricsService.getAdvancedMetrics());
    }
}
