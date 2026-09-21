package com.cbo.players.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
    @NotBlank(message = "El nombre de usuario es obligatorio")
    String username,

    @NotBlank(message = "La contraseña es obligatoria")
    String password
) {}
