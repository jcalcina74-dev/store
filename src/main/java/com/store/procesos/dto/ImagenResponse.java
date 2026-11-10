package com.store.procesos.dto;

public record ImagenResponse(
    Long id, String url, Integer orden, Integer ancho, Integer alto, Long tamanioBytes) {}
