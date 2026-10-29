package com.cbo.players.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequestDto(
        @NotNull(message = "El identificador del jugador es obligatorio")
        Long playerId,

        @NotNull(message = "La cantidad de tokens es obligatoria")
        @Min(value = 1, message = "La cantidad de tokens debe ser mayor a 0")
        Integer quantity
) {}
