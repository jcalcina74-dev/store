package com.store.procesos.dto;

import jakarta.validation.constraints.*;

public record MarcaRequest(@NotBlank @Size(max = 80) String nombre) {}
