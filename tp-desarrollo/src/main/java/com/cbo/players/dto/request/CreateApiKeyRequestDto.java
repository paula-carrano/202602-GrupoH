package com.cbo.players.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequestDto(
    @NotBlank(message = "El nombre de la clave es obligatorio")
    @Size(min = 3, max = 50, message = "El nombre de la clave debe tener entre 3 y 50 caracteres")
    String name
) {}
