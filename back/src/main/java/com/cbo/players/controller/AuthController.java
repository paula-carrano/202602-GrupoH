package com.cbo.players.controller;

import com.cbo.players.config.OpenApiConfig;
import com.cbo.players.dto.request.CreateApiKeyRequestDto;
import com.cbo.players.dto.request.LoginRequestDto;
import com.cbo.players.dto.request.RegisterRequestDto;
import com.cbo.players.dto.response.ApiKeyCreatedResponseDto;
import com.cbo.players.dto.response.LoginResponseDto;
import com.cbo.players.dto.response.UserResponseDto;
import com.cbo.players.security.UserPrincipal;
import com.cbo.players.service.ApiKeyService;
import com.cbo.players.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Endpoints de registro, inicio de sesión y gestión de API Keys")
public class AuthController {

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    public AuthController(AuthService authService, ApiKeyService apiKeyService) {
        this.authService = authService;
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario", description = "Crea una nueva cuenta de usuario con rol USER por defecto")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos o error de validación"),
            @ApiResponse(responseCode = "409", description = "El correo electrónico o nombre de usuario ya existe")
    })
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        UserResponseDto response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Autentica credenciales y emite un token JWT Bearer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa con token JWT"),
            @ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas")
    })
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        LoginResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api-keys")
    @Operation(
            summary = "Generar nueva API Key",
            description = "Crea una API Key para acceso programático M2M. La clave en texto plano solo se devuelve una única vez en esta respuesta.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "API Key creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado o token JWT inválido")
    })
    public ResponseEntity<ApiKeyCreatedResponseDto> createApiKey(
            @Valid @RequestBody CreateApiKeyRequestDto request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        ApiKeyCreatedResponseDto response = apiKeyService.createApiKey(request, userPrincipal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
