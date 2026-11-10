package com.store.procesos.realtime;

import static org.junit.jupiter.api.Assertions.*;
import com.store.procesos.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CatalogoEventStreamTests {
  @Test
  void limitaConexionesYRechazaSuscripcionesAlCerrar() {
    var stream = new CatalogoEventStream(1, 5000, 1000);
    try {
      stream.suscribir();
      var error = assertThrows(ApiException.class, stream::suscribir);
      assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    } finally {
      stream.cerrar();
    }
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
        assertThrows(ApiException.class, stream::suscribir).getStatus());
  }
}