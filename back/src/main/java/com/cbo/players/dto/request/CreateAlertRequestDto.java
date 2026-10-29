package com.cbo.players.dto.request;

import com.cbo.players.model.AlertCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateAlertRequestDto(
        @NotNull(message = "El identificador del jugador es obligatorio")
        Long playerId,

        @NotNull(message = "La condición de la alerta es obligatoria (ABOVE o BELOW)")
        AlertCondition condition,

        @NotNull(message = "El precio objetivo es obligatorio")
        @DecimalMin(value = "0.0001", inclusive = true, message = "El precio objetivo debe ser un valor positivo mayor a 0")
        BigDecimal targetPrice
) {}
