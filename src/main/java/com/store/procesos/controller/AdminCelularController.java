package com.store.procesos.controller;

import com.store.procesos.dto.*;
import com.store.procesos.service.CelularService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/celulares")
@SecurityRequirement(name = "basicAuth")
@Tag(name = "Administracion - Celulares")
public class AdminCelularController {
  private final CelularService service;

  public AdminCelularController(CelularService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Listar todos los celulares, incluidos inactivos")
  public PaginaResponse<CelularResponse> listar(
      @RequestParam(required = false) Long marcaId,
      @RequestParam(required = false) String texto,
      @RequestParam(required = false) BigDecimal precioMin,
      @RequestParam(required = false) BigDecimal precioMax,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size,
      @RequestParam(defaultValue = "id,desc") String sort) {
    return service.listar(false, marcaId, texto, precioMin, precioMax, page, size, sort);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar celular activo o inactivo")
  public CelularResponse obtener(@PathVariable Long id) {
    return service.obtener(id, false);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crear celular")
  @ApiResponse(responseCode = "201", description = "Creado")
  public ResponseEntity<CelularResponse> crear(@Valid @RequestBody CelularRequest dto) {
    var c = service.crear(dto);
    return ResponseEntity.created(URI.create("/api/admin/celulares/" + c.id())).body(c);
  }

  @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Actualizar todos los datos de un celular")
  public CelularResponse actualizar(@PathVariable Long id, @Valid @RequestBody CelularRequest dto) {
    return service.actualizar(id, dto);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Eliminar celular y sus imagenes")
  public void eliminar(@PathVariable Long id) {
    service.eliminar(id);
  }

  @PostMapping(value = "/{id}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Subir imagen; maximo 8 imagenes y 5 MiB por archivo")
  @ApiResponse(responseCode = "201", description = "Imagen convertida y guardada")
  @ApiResponse(responseCode = "413", description = "Imagen demasiado grande")
  public ResponseEntity<ImagenResponse> imagen(
      @PathVariable Long id,
      @RequestPart("archivo") MultipartFile archivo,
      @Parameter(description = "Orden unico dentro del celular, desde 0") @RequestParam int orden) {
    var image = service.agregarImagen(id, archivo, orden);
    return ResponseEntity.created(URI.create(image.url())).body(image);
  }

  @DeleteMapping("/{id}/imagenes/{imagenId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Eliminar imagen")
  public void eliminarImagen(@PathVariable Long id, @PathVariable Long imagenId) {
    service.eliminarImagen(id, imagenId);
  }
}
