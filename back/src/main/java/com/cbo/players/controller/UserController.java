package com.cbo.players.controller;

import com.cbo.players.dto.request.CreditsTopupRequestDto;
import com.cbo.players.dto.response.CreditsTopupResponseDto;
import com.cbo.players.dto.response.PagedResponseDto;
import com.cbo.players.dto.response.PortfolioResponseDto;
import com.cbo.players.dto.response.TransactionResponseDto;
import com.cbo.players.security.UserPrincipal;
import com.cbo.players.service.PortfolioService;
import com.cbo.players.service.TransactionService;
import com.cbo.players.service.WalletService;
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
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Gestión de usuarios, billetera, portfolio y transacciones")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final WalletService walletService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;

    public UserController(WalletService walletService,
                          PortfolioService portfolioService,
                          TransactionService transactionService) {
        this.walletService = walletService;
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
    }

    @PostMapping("/{id}/credits/topup")
    @Operation(summary = "Recarga simulada de créditos", description = "Recarga créditos en la cuenta del usuario para operar en el mercado de tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recarga acreditada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Monto inválido"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado a la billetera de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<CreditsTopupResponseDto> topupCredits(
            @PathVariable Long id,
            @Valid @RequestBody CreditsTopupRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long actorId = principal != null ? principal.getId() : id;
        boolean isPrivileged = isUserPrivileged(principal);

        CreditsTopupResponseDto response = walletService.topupCredits(id, request.amount(), actorId, isPrivileged);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/portfolio")
    @Operation(summary = "Obtener portfolio consolidado del usuario", description = "Devuelve el portfolio del usuario con métricas de valor actual, costo base y PnL nominal y porcentual.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio obtenido exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado al portfolio de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<PortfolioResponseDto> getPortfolio(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long actorId = principal != null ? principal.getId() : id;
        boolean isPrivileged = isUserPrivileged(principal);

        PortfolioResponseDto response = portfolioService.getUserPortfolio(id, actorId, isPrivileged);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Historial paginado de transacciones del usuario", description = "Devuelve la serie cronológica inmutable de operaciones (recargas, compras y ventas) ejecutadas por el usuario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial de transacciones obtenido exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado al historial de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<PagedResponseDto<TransactionResponseDto>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long actorId = principal != null ? principal.getId() : id;
        boolean isPrivileged = isUserPrivileged(principal);

        PagedResponseDto<TransactionResponseDto> response = transactionService.getUserTransactions(id, page, size, actorId, isPrivileged);
        return ResponseEntity.ok(response);
    }

    private boolean isUserPrivileged(UserPrincipal principal) {
        return principal != null && principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPERUSER"));
    }
}
