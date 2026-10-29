package com.cbo.players.controller;

import com.cbo.players.dto.request.CreateAlertRequestDto;
import com.cbo.players.dto.response.PriceAlertResponseDto;
import com.cbo.players.security.UserPrincipal;
import com.cbo.players.service.AlertService;
import com.cbo.players.service.AlertStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Motor de alertas de precios y notificaciones push en tiempo real vía Server-Sent Events (SSE)")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertService alertService;
    private final AlertStreamService alertStreamService;

    public AlertController(AlertService alertService, AlertStreamService alertStreamService) {
        this.alertService = alertService;
        this.alertStreamService = alertStreamService;
    }

    @PostMapping
    @Operation(summary = "Crear alerta de precio", description = "Registra un umbral de cotización para un jugador que notificará al usuario vía SSE cuando se alcance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Alerta creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    })
    public ResponseEntity<PriceAlertResponseDto> createAlert(
            @Valid @RequestBody CreateAlertRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : 1L;
        PriceAlertResponseDto response = alertService.createAlert(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar alertas de precio del usuario", description = "Devuelve todas las alertas de precio configuradas por el usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Listado de alertas obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public ResponseEntity<List<PriceAlertResponseDto>> getUserAlerts(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : 1L;
        return ResponseEntity.ok(alertService.getUserAlerts(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar alerta de precio", description = "Cancela o elimina una alerta de precio activa perteneciente al usuario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Alerta eliminada exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado a la alerta de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada")
    })
    public ResponseEntity<Void> deleteAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : 1L;
        boolean isPrivileged = principal != null && principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPERUSER"));

        alertService.deleteAlert(id, userId, isPrivileged);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Suscripción al canal Server-Sent Events (SSE)", description = "Establece una conexión persistente de eventos para recibir notificaciones push instantáneas cuando se disparen las alertas.")
    @ApiResponse(responseCode = "200", description = "Conexión SSE establecida exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public SseEmitter streamAlerts(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : 1L;
        return alertStreamService.subscribe(userId);
    }
}
