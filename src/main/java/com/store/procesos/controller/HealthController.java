package com.store.procesos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Estado")
public class HealthController {
  @GetMapping("/api/health")
  @Operation(summary = "Comprobar que el servidor responde (sin consultar servicios externos)")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
