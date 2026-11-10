package com.store.procesos.controller;

import com.store.procesos.dto.*;
import com.store.procesos.service.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogo")
@Tag(name = "Catalogo publico")
public class CatalogoController {
  private final CelularService celulares;
  private final MarcaService marcas;

  public CatalogoController(CelularService celulares, MarcaService marcas) {
    this.celulares = celulares;
    this.marcas = marcas;
  }

  @GetMapping("/celulares")
  @Operation(summary = "Listar celulares activos con filtros y paginacion")
  public PaginaResponse<CelularResponse> listar(
      @RequestParam(required = false) Long marcaId,
      @RequestParam(required = false) String texto,
      @RequestParam(required = false) BigDecimal precioMin,
      @RequestParam(required = false) BigDecimal precioMax,
      @Parameter(description = "Pagina desde 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Elementos por pagina: 1 a 50") @RequestParam(defaultValue = "12")
          int size,
      @Parameter(description = "id,desc | precio,asc | modelo,asc; desempate por id")
          @RequestParam(defaultValue = "id,desc")
          String sort) {
    return celulares.listar(true, marcaId, texto, precioMin, precioMax, page, size, sort);
  }

  @GetMapping("/celulares/{id}")
  @Operation(summary = "Consultar detalle de un celular activo")
  public CelularResponse obtener(@PathVariable Long id) {
    return celulares.obtener(id, true);
  }

  @GetMapping("/marcas")
  @Operation(summary = "Listar marcas")
  public List<MarcaResponse> marcas() {
    return marcas.listar();
  }
}
