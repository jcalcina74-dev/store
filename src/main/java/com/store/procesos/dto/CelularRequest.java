package com.store.procesos.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CelularRequest(
    @NotNull @Positive Long marcaId,
    @NotBlank @Size(max = 120) String modelo,
    @Size(max = 5000) String descripcion,
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal precio,
    @NotNull @PositiveOrZero Integer stock,
    @NotNull @Positive @Max(256) Integer ramGb,
    @NotNull @Positive @Max(16384) Integer almacenamientoGb,
    @NotNull @DecimalMin("0.01") @DecimalMax("99.99") @Digits(integer = 2, fraction = 2)
        BigDecimal pantallaPulgadas,
    @NotNull @Positive @Max(100000) Integer bateriaMah,
    @NotBlank @Size(max = 80) String sistemaOperativo,
    @NotBlank @Size(max = 50) String color,
    @NotNull Boolean activo) {}
