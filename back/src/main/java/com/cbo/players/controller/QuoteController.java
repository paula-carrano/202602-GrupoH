package com.cbo.players.controller;

import com.cbo.players.dto.response.QuoteRecalculateResponseDto;
import com.cbo.players.service.QuoteCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
@Tag(name = "Quotes", description = "Operaciones y recálculo global de cotizaciones de mercado")
@SecurityRequirement(name = "bearerAuth")
public class QuoteController {

    private final QuoteCalculationService quoteCalculationService;

    public QuoteController(QuoteCalculationService quoteCalculationService) {
        this.quoteCalculationService = quoteCalculationService;
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERUSER')")
    @Operation(summary = "Recalcular cotizaciones globalmente", description = "Recalcula las cotizaciones actuales de todos los jugadores activos aplicando la estrategia activa, emite eventos y limpia caché.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recálculo ejecutado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado: rol ADMIN o SUPERUSER requerido")
    })
    public ResponseEntity<QuoteRecalculateResponseDto> recalculateQuotes() {
        QuoteRecalculateResponseDto response = quoteCalculationService.recalculateAllQuotes();
        return ResponseEntity.ok(response);
    }
}
