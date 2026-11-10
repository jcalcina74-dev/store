package com.store.procesos.dto;

import java.math.BigDecimal;
import java.util.List;

public record CelularResponse(
    Long id,
    MarcaResponse marca,
    String modelo,
    String descripcion,
    BigDecimal precio,
    String moneda,
    Integer stock,
    Integer ramGb,
    Integer almacenamientoGb,
    BigDecimal pantallaPulgadas,
    Integer bateriaMah,
    String sistemaOperativo,
    String color,
    Boolean activo,
    List<ImagenResponse> imagenes) {}
