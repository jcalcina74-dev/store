package com.store.procesos.controller;

import com.store.procesos.realtime.CatalogoEventStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/catalogo")
@Tag(name = "Catalogo publico")
public class CatalogoEventosController {
  private final CatalogoEventStream events;

  public CatalogoEventosController(CatalogoEventStream events) {
    this.events = events;
  }

  @GetMapping(value = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Escuchar cambios del catalogo en tiempo real (SSE)",
      description = "Canal publico. Al conectar emite sincronizar; tras cambios confirmados emite "
          + "catalogo-actualizado. Cada aviso contiene solo version: volver a consultar la pagina "
          + "y los filtros actuales. Comentarios keep-alive mantienen la conexion. Reconectar al "
          + "cerrarse el canal; no hay historial ni replay. Swagger mantiene la solicitud abierta.",
      parameters = @Parameter(name = "Last-Event-ID", in = ParameterIn.HEADER,
          description = "Opcional. No se reproduce historial: siempre se solicita sincronizar al conectar."),
      responses = {
        @ApiResponse(responseCode = "200", description = "Flujo SSE abierto",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                schema = @Schema(type = "string"),
                examples = @ExampleObject(value = "event: catalogo-actualizado\nid: instancia:3\nretry: 3000\ndata: {\"version\":\"instancia:3\"}\n\n"))),
        @ApiResponse(responseCode = "503", description = "Limite de conexiones alcanzado; reintentar con espera")
      })
  public ResponseEntity<SseEmitter> eventos() {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .cacheControl(CacheControl.noStore())
        .header("X-Accel-Buffering", "no")
        .body(events.suscribir());
  }
}