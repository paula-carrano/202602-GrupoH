package com.cbo.players.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreditsTopupRequestDto(
        @NotNull(message = "El monto a recargar es obligatorio")
        @DecimalMin(value = "0.0001", inclusive = true, message = "El monto a recargar debe ser un valor positivo válido (mayor a 0 y hasta 10000 CRD).")
        @DecimalMax(value = "10000.0000", inclusive = true, message = "El monto a recargar debe ser un valor positivo válido (mayor a 0 y hasta 10000 CRD).")
        BigDecimal amount
) {}
