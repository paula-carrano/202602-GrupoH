package com.cbo.players.controller;

import com.cbo.players.dto.request.OrderRequestDto;
import com.cbo.players.dto.response.OrderResponseDto;
import com.cbo.players.security.UserPrincipal;
import com.cbo.players.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Mercado atómico de compra y venta de tokens de jugadores")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/buy")
    @Operation(summary = "Ejecutar orden de compra de tokens", description = "Adquiere tokens de un jugador activo descontando créditos de la billetera y transfiriendo tokens del pool del superusuario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orden de compra ejecutada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente o tokens no disponibles"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    })
    public ResponseEntity<OrderResponseDto> buyTokens(
            @Valid @RequestBody OrderRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long buyerUserId = principal != null ? principal.getId() : 1L;
        OrderResponseDto response = orderService.executeBuyOrder(buyerUserId, request.playerId(), request.quantity());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sell")
    @Operation(summary = "Ejecutar orden de venta de tokens", description = "Vende tokens de un jugador acreditando fondos en la billetera y reintegrando tokens al pool del superusuario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orden de venta ejecutada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Tenencia de tokens insuficiente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    })
    public ResponseEntity<OrderResponseDto> sellTokens(
            @Valid @RequestBody OrderRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long sellerUserId = principal != null ? principal.getId() : 1L;
        OrderResponseDto response = orderService.executeSellOrder(sellerUserId, request.playerId(), request.quantity());
        return ResponseEntity.ok(response);
    }
}
