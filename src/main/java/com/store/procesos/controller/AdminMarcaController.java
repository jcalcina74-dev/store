package com.store.procesos.controller;

import com.store.procesos.dto.*;
import com.store.procesos.service.MarcaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/marcas")
@SecurityRequirement(name = "basicAuth")
@Tag(name = "Administracion - Marcas")
public class AdminMarcaController {
  private final MarcaService service;

  public AdminMarcaController(MarcaService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Listar marcas")
  public List<MarcaResponse> listar() {
    return service.listar();
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crear marca")
  @ApiResponse(responseCode = "201", description = "Creada")
  public ResponseEntity<MarcaResponse> crear(@Valid @RequestBody MarcaRequest dto) {
    var m = service.crear(dto);
    return ResponseEntity.created(URI.create("/api/admin/marcas")).body(m);
  }

  @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Actualizar marca")
  public MarcaResponse actualizar(@PathVariable Long id, @Valid @RequestBody MarcaRequest dto) {
    return service.actualizar(id, dto);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Eliminar marca sin celulares asociados")
  public void eliminar(@PathVariable Long id) {
    service.eliminar(id);
  }
}
