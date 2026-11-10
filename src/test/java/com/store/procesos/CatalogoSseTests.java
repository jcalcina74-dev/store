package com.store.procesos;

import static org.junit.jupiter.api.Assertions.*;

import com.store.procesos.dto.*;
import com.store.procesos.service.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.sse.heartbeat-ms=1000", "app.sse.timeout-ms=30000",
    "app.sse.dispatch-ms=50", "app.sse.max-clients=32"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogoSseTests {
  @TempDir static Path media;
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("app.media-dir", media::toString);
  }

  @LocalServerPort int port;
  @Autowired MarcaService marcas;
  @Autowired CelularService celulares;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();

  @BeforeEach
  void limpiar() {
    jdbc.update("DELETE FROM \"T_ImagenCelular\"");
    jdbc.update("DELETE FROM \"T_Celular\"");
    jdbc.update("DELETE FROM \"T_Marca\"");
  }

  @Test
  void canalPublicoConSincronizacionYHeartbeat() throws Exception {
    try (var stream = conectar(null)) {
      assertTrue(stream.response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
      assertTrue(stream.response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
      assertEquals("no", stream.response.headers().firstValue("X-Accel-Buffering").orElse(""));
      var initial = stream.evento("sincronizar");
      var data = mapper.readTree(initial.data());
      assertEquals(1, data.size());
      assertEquals(initial.id(), data.get("version").asString());
      assertTrue(initial.raw().contains("retry:3000"));
      assertTrue(stream.comentario().raw().contains("keep-alive"));
    }
  }

  @Test
  void todosLosCambiosLleganADosTelefonosSinExponerDatos() throws Exception {
    try (var a = conectar(null); var b = conectar(null)) {
      a.evento("sincronizar"); b.evento("sincronizar");

      var marca = marcas.crear(new MarcaRequest("Samsung"));
      cambioEnAmbos(a, b);
      var celular = celulares.crear(dto(marca.id(), true, "2300"));
      cambioEnAmbos(a, b);
      celulares.actualizar(celular.id(), dto(marca.id(), false, "2200"));
      cambioEnAmbos(a, b);

      var out = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
      var imagen = celulares.agregarImagen(celular.id(),
          new MockMultipartFile("archivo", "foto.png", "image/png", out.toByteArray()), 0);
      cambioEnAmbos(a, b);
      celulares.eliminarImagen(celular.id(), imagen.id());
      cambioEnAmbos(a, b);
      celulares.eliminar(celular.id());
      cambioEnAmbos(a, b);
      marcas.actualizar(marca.id(), new MarcaRequest("Samsung editada"));
      cambioEnAmbos(a, b);
      marcas.eliminar(marca.id());
      cambioEnAmbos(a, b);
    }
  }

  @Test
  void noSeAvisaAntesDelCommit() throws Exception {
    var marca = marcas.crear(new MarcaRequest("Google"));
    try (var stream = conectar(null)) {
      stream.evento("sincronizar");
      new TransactionTemplate(transactions).executeWithoutResult(tx -> {
        marcas.actualizar(marca.id(), new MarcaRequest("Google actualizado"));
        stream.sinCambio(350);
      });
      stream.evento("catalogo-actualizado");
      assertEquals("google actualizado", marcas.listar().get(0).nombre());
    }
  }

  @Test
  void rollbackYConflictosNoProducenAvisos() throws Exception {
    marcas.crear(new MarcaRequest("Apple"));
    try (var stream = conectar(null)) {
      stream.evento("sincronizar");
      new TransactionTemplate(transactions).executeWithoutResult(tx -> {
        marcas.crear(new MarcaRequest("No debe persistir"));
        stream.sinCambio(250);
        tx.setRollbackOnly();
      });
      stream.sinCambio(350);
      assertThrows(DataIntegrityViolationException.class,
          () -> marcas.crear(new MarcaRequest("APPLE")));
      stream.sinCambio(350);
      assertEquals(1, marcas.listar().size());
    }
  }

  @Test
  void reconectarSiempreSolicitaEstadoActualAunqueHayaLastEventId() throws Exception {
    String previous;
    try (var stream = conectar(null)) {
      previous = stream.evento("sincronizar").id();
    }
    marcas.crear(new MarcaRequest("Motorola"));
    try (var stream = conectar(previous)) {
      var initial = stream.evento("sincronizar");
      assertNotEquals(previous, initial.id());
      var response = http.send(HttpRequest.newBuilder(url("/api/catalogo/marcas")).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("motorola"));
    }
  }

  private CelularRequest dto(Long marca, boolean activo, String precio) {
    return new CelularRequest(marca, "Galaxy", "Prueba", new BigDecimal(precio), 2, 8, 256,
        new BigDecimal("6.50"), 5000, "Android", "Negro", activo);
  }

  private void cambioEnAmbos(Stream a, Stream b) throws Exception {
    var first = a.evento("catalogo-actualizado");
    var second = b.evento("catalogo-actualizado");
    assertEquals(first.id(), second.id());
    var data = mapper.readTree(first.data());
    assertEquals(1, data.size(), "El canal publico solo expone una version opaca");
    assertEquals(first.id(), data.get("version").asString());
  }

  private URI url(String path) { return URI.create("http://127.0.0.1:" + port + path); }

  private Stream conectar(String lastId) throws Exception {
    var request = HttpRequest.newBuilder(url("/api/catalogo/eventos"))
        .timeout(Duration.ofSeconds(5)).header("Accept", "text/event-stream");
    if (lastId != null) request.header("Last-Event-ID", lastId);
    var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    assertEquals(200, response.statusCode());
    return new Stream(response);
  }

  private record Frame(String event, String id, String data, String raw) {
    static Frame parse(String raw) {
      String name = "", id = "", data = "";
      for (String line : raw.split("\n")) {
        if (line.startsWith("event:")) name = line.substring(6).strip();
        if (line.startsWith("id:")) id = line.substring(3).strip();
        if (line.startsWith("data:")) data += line.substring(5).strip();
      }
      return new Frame(name, id, data, raw);
    }
  }

  private static final class Stream implements AutoCloseable {
    private final HttpResponse<InputStream> response;
    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private final ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
      var thread = new Thread(task, "test-sse-reader");
      thread.setDaemon(true);
      return thread;
    });

    private Stream(HttpResponse<InputStream> response) {
      this.response = response;
      reader.submit(() -> {
        try (var lines = new BufferedReader(new InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
          StringBuilder frame = new StringBuilder();
          String line;
          while ((line = lines.readLine()) != null) {
            if (line.isEmpty()) {
              if (!frame.isEmpty()) frames.add(Frame.parse(frame.toString()));
              frame.setLength(0);
            } else frame.append(line).append('\n');
          }
        } catch (IOException ignored) {
          // Cierre esperado al terminar la prueba o la conexion.
        }
      });
    }

    private Frame evento(String event) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline) {
        var frame = frames.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        if (frame != null && frame.event().equals(event)) return frame;
      }
      throw new AssertionError("No se recibio SSE: " + event);
    }

    private Frame comentario() throws InterruptedException {
      var frame = frames.poll(3, TimeUnit.SECONDS);
      assertNotNull(frame, "Falta heartbeat");
      assertTrue(frame.raw().startsWith(":"));
      return frame;
    }

    private void sinCambio(long millis) {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
      try {
        while (System.nanoTime() < deadline) {
          var frame = frames.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
          if (frame != null) assertNotEquals("catalogo-actualizado", frame.event());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }

    @Override public void close() throws IOException {
      response.body().close();
      reader.shutdownNow();
    }
  }
}