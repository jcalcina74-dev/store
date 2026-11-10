package com.store.procesos;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.store.procesos.repository.*;
import com.store.procesos.service.CelularService;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProcesosApplicationTests {
  @TempDir static Path media;

  @DynamicPropertySource
  static void mediaDirectory(DynamicPropertyRegistry r) {
    r.add("app.media-dir", media::toString);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired CelularService service;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired ImagenCelularRepository imagenes;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
  private static final String USER = "testadmin", PASS = "Test-password-2026";

  @BeforeEach
  void clear() throws Exception {
    jdbc.update("DELETE FROM \"T_ImagenCelular\"");
    jdbc.update("DELETE FROM \"T_Celular\"");
    jdbc.update("DELETE FROM \"T_Marca\"");
    try (var stream = Files.list(media.resolve("celulares"))) {
      for (var p : stream.toList()) Files.delete(p);
    }
  }

  private JsonNode json(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private long marca(String nombre) throws Exception {
    return json(mvc.perform(
                post("/api/admin/marcas")
                    .with(httpBasic(USER, PASS))
                    .header("X-Admin-Request", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("nombre", nombre))))
            .andExpect(status().isCreated())
            .andReturn())
        .get("id")
        .asLong();
  }

  private String body(long marca, String modelo, boolean activo, int precio) throws Exception {
    return mapper.writeValueAsString(
        Map.ofEntries(
            Map.entry("marcaId", marca),
            Map.entry("modelo", modelo),
            Map.entry("descripcion", "Celular de prueba"),
            Map.entry("precio", precio),
            Map.entry("stock", 2),
            Map.entry("ramGb", 8),
            Map.entry("almacenamientoGb", 256),
            Map.entry("pantallaPulgadas", 6.5),
            Map.entry("bateriaMah", 5000),
            Map.entry("sistemaOperativo", "Android"),
            Map.entry("color", "Negro"),
            Map.entry("activo", activo)));
  }

  private long celular(long marca, String modelo, boolean activo, int precio) throws Exception {
    return json(mvc.perform(
                post("/api/admin/celulares")
                    .with(httpBasic(USER, PASS))
                    .header("X-Admin-Request", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(marca, modelo, activo, precio)))
            .andExpect(status().isCreated())
            .andReturn())
        .get("id")
        .asLong();
  }

  private MockMultipartFile png(int width, int height) throws Exception {
    var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    img.setRGB(0, 0, 0x80ff0000);
    var out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return new MockMultipartFile(
        "archivo", "../../inseguro.exe", "application/octet-stream", out.toByteArray());
  }

  private long upload(long id, int orden) throws Exception {
    return json(mvc.perform(
                multipart("/api/admin/celulares/" + id + "/imagenes")
                    .file(png(32, 16))
                    .param("orden", "" + orden)
                    .with(httpBasic(USER, PASS))
                    .header("X-Admin-Request", "true"))
            .andExpect(status().isCreated())
            .andReturn())
        .get("id")
        .asLong();
  }

  @Test
  void catalogoPublicoSinAutenticacion() throws Exception {
    mvc.perform(get("/api/catalogo/celulares"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido").isEmpty())
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.tamanio").value(12));
    mvc.perform(get("/api/catalogo/marcas")).andExpect(status().isOk());
  }

  @Test
  void administracionRequiereCredencialesYHeaderContraCsrf() throws Exception {
    mvc.perform(get("/api/admin/celulares"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    mvc.perform(get("/api/admin/celulares").with(httpBasic(USER, "incorrecta")))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/admin/marcas")
                .with(httpBasic(USER, PASS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/catalogo/celulares").with(httpBasic(USER, PASS)))
        .andExpect(status().isForbidden());
  }

  @Test
  void cicloCrudYVisibilidad() throws Exception {
    long m = marca("Samsung"), id = celular(m, "S26", false, 4000);
    mvc.perform(get("/api/catalogo/celulares/" + id)).andExpect(status().isNotFound());
    mvc.perform(get("/api/catalogo/celulares")).andExpect(jsonPath("$.totalElementos").value(0));
    mvc.perform(get("/api/admin/celulares").with(httpBasic(USER, PASS)))
        .andExpect(jsonPath("$.totalElementos").value(1));
    mvc.perform(
            put("/api/admin/celulares/" + id)
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(m, "S26", true, 3500)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/catalogo/celulares/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moneda").value("BOB"));
    mvc.perform(
            delete("/api/admin/celulares/" + id)
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete("/api/admin/marcas/" + m)
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isNoContent());
  }

  @Test
  void paginacionFiltrosYOrdenEstable() throws Exception {
    long m = marca("Google");
    celular(m, "Pixel A", true, 2000);
    celular(m, "Pixel B", true, 3000);
    long c = celular(m, "Pixel C", true, 3000);
    celular(m, "Pixel oculto", false, 2500);
    mvc.perform(
            get("/api/catalogo/celulares")
                .param("size", "1")
                .param("sort", "precio,desc")
                .param("marcaId", "" + m)
                .param("texto", "PIXEL")
                .param("precioMin", "2000")
                .param("precioMax", "3000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].id").value(c))
        .andExpect(jsonPath("$.totalElementos").value(3))
        .andExpect(jsonPath("$.totalPaginas").value(3));
    mvc.perform(get("/api/catalogo/celulares").param("page", "1").param("size", "2"))
        .andExpect(jsonPath("$.contenido.length()").value(1));
    mvc.perform(get("/api/catalogo/celulares").param("texto", "%"))
        .andExpect(jsonPath("$.totalElementos").value(0));
  }

  @Test
  void rechazaParametrosDePaginacionYPrecioInvalidos() throws Exception {
    for (var p :
        List.of(
            Map.of("size", "51"),
            Map.of("size", "0"),
            Map.of("page", "-1"),
            Map.of("sort", "descripcion,asc"),
            Map.of("sort", "precio,no"),
            Map.of("precioMin", "-1"),
            Map.of("precioMin", "3", "precioMax", "2"),
            Map.of("page", "2147483647"),
            Map.of("marcaId", "0"))) {
      var request = get("/api/catalogo/celulares");
      p.forEach(request::param);
      mvc.perform(request).andExpect(status().isBadRequest());
    }
    mvc.perform(get("/api/catalogo/celulares/no-numero")).andExpect(status().isBadRequest());
  }

  @Test
  void validacionDeCuerpoYDuplicadosNormalizados() throws Exception {
    long m = marca("  Samsung  ");
    mvc.perform(
            post("/api/admin/marcas")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"SAMSUNG\"}"))
        .andExpect(status().isConflict());
    celular(m, "Galaxy S", true, 3000);
    mvc.perform(
            post("/api/admin/celulares")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(m, " GALAXY   S ", true, 3000)))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/admin/celulares")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores").isArray());
    mvc.perform(
            post("/api/admin/celulares")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(m, "Otro", true, 0)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void noPermiteEliminarMarcaConCelulares() throws Exception {
    long m = marca("Motorola");
    celular(m, "Moto", true, 1200);
    mvc.perform(
            delete("/api/admin/marcas/" + m)
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isConflict());
  }

  @Test
  void convierteImagenAWebpYLaEliminaConElCelular() throws Exception {
    long id = celular(marca("Apple"), "iPhone", true, 5000);
    long image =
        json(mvc.perform(
                    multipart("/api/admin/celulares/" + id + "/imagenes")
                        .file(png(2000, 1000))
                        .param("orden", "0")
                        .with(httpBasic(USER, PASS))
                        .header("X-Admin-Request", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ancho").value(1600))
                .andExpect(jsonPath("$.alto").value(800))
                .andExpect(jsonPath("$.rutaRelativa").doesNotExist())
                .andReturn())
            .get("id")
            .asLong();
    var result =
        mvc.perform(get("/api/media/" + image))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/webp"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn();
    byte[] bytes = result.getResponse().getContentAsByteArray();
    assertEquals("RIFF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
    assertEquals(1600, decoded.getWidth());
    assertTrue(decoded.getColorModel().hasAlpha());
    mvc.perform(
            get("/api/media/" + image)
                .header("If-None-Match", result.getResponse().getHeader("ETag")))
        .andExpect(status().isNotModified());
    mvc.perform(
            delete("/api/admin/celulares/" + id)
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/media/" + image)).andExpect(status().isNotFound());
    try (var files = Files.list(media.resolve("celulares"))) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void imagenInactivaSoloVisibleParaAdmin() throws Exception {
    long id = celular(marca("Honor"), "X", false, 900), image = upload(id, 0);
    mvc.perform(get("/api/media/" + image)).andExpect(status().isNotFound());
    mvc.perform(get("/api/media/" + image).with(httpBasic(USER, PASS))).andExpect(status().isOk());
  }

  @Test
  void rechazaArchivoInvalidoYSinArchivo() throws Exception {
    long id = celular(marca("OnePlus"), "12", true, 2300);
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .file(
                    new MockMultipartFile(
                        "archivo", "x.png", "image/png", "no soy una imagen".getBytes()))
                .param("orden", "0")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .param("orden", "0")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void limitaCantidadYOrdenDeImagenes() throws Exception {
    long id = celular(marca("Nokia"), "N", true, 700);
    upload(id, 0);
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .file(png(4, 4))
                .param("orden", "0")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isConflict());
    for (int n = 1; n < 8; n++) upload(id, n);
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .file(png(4, 4))
                .param("orden", "8")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isConflict());
    assertEquals(8, imagenes.countByCelularId(id));
  }

  @Test
  void noEliminaImagenDeOtroCelular() throws Exception {
    long m = marca("Sony"),
        a = celular(m, "A", true, 1000),
        b = celular(m, "B", true, 1100),
        image = upload(a, 0);
    mvc.perform(
            delete("/api/admin/celulares/" + b + "/imagenes/" + image)
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/media/" + image)).andExpect(status().isOk());
    mvc.perform(
            delete("/api/admin/celulares/" + a + "/imagenes/" + image)
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isNoContent());
    try (var files = Files.list(media.resolve("celulares"))) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void rollbackBorraArchivoNuevo() throws Exception {
    long id = celular(marca("Realme"), "R", true, 1500);
    var file = png(16, 16);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx -> {
              service.agregarImagen(id, file, 0);
              tx.setRollbackOnly();
            });
    assertEquals(0, imagenes.countByCelularId(id));
    try (var files = Files.list(media.resolve("celulares"))) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void cuentaUnicaYPasswordHasheado() {
    var hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM \"T_Administrador\" WHERE id=1", String.class);
    assertNotEquals(PASS, hash);
    assertTrue(encoder.matches(PASS, hash));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO \"T_Administrador\"(id,usuario,password_hash) VALUES"
                    + " (2,'otro','hash')"));
  }

  @Test
  void swaggerDocumentaTodosLosEndpointsYProtegeDocsEnProduccion() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    var spec =
        json(
            mvc.perform(get("/v3/api-docs").with(httpBasic(USER, PASS)))
                .andExpect(status().isOk())
                .andReturn());
    var paths = spec.get("paths");
    for (String path :
        List.of(
            "/api/catalogo/celulares",
            "/api/catalogo/celulares/{id}",
            "/api/catalogo/marcas",
            "/api/catalogo/eventos",
            "/api/admin/celulares",
            "/api/admin/celulares/{id}",
            "/api/admin/celulares/{id}/imagenes",
            "/api/admin/celulares/{id}/imagenes/{imagenId}",
            "/api/admin/marcas",
            "/api/admin/marcas/{id}",
            "/api/media/{imagenId}")) assertTrue(paths.has(path), path);
    assertTrue(spec.get("components").get("securitySchemes").has("basicAuth"));
    mvc.perform(get("/swagger-ui/index.html").with(httpBasic(USER, PASS)))
        .andExpect(status().isOk());
  }

  @Test
  void corsRechazaOrigenNoAutorizado() throws Exception {
    mvc.perform(
            options("/api/admin/marcas")
                .header("Origin", "https://no-autorizado.test")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Admin-Request"))
        .andExpect(status().isForbidden());
    mvc.perform(
            options("/api/admin/marcas")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Admin-Request"))
        .andExpect(status().isOk());
  }

  @Test
  void rechazaAnimacionDimensionExcesivaYTamanioExcesivo() throws Exception {
    long id = celular(marca("Asus"), "Zenfone", true, 2000);
    byte[] oversized = new byte[5 * 1024 * 1024 + 1];
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .file(new MockMultipartFile("archivo", "x.png", "image/png", oversized))
                .param("orden", "0")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isPayloadTooLarge());
    byte[] png = png(8, 8).getBytes();
    // Modificar IHDR para declarar 25 megapixeles sin reservar una imagen gigante.
    for (int offset : new int[] {16, 20}) {
      png[offset] = 0;
      png[offset + 1] = 0;
      png[offset + 2] = 0x13;
      png[offset + 3] = (byte) 0x88;
    }
    var crc = new java.util.zip.CRC32();
    crc.update(png, 12, 17);
    long checksum = crc.getValue();
    for (int n = 0; n < 4; n++) png[29 + n] = (byte) (checksum >>> (24 - 8 * n));
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .file(new MockMultipartFile("archivo", "x.png", "image/png", png))
                .param("orden", "0")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isBadRequest());
    byte[] anim = new byte[30];
    System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, anim, 0, 4);
    System.arraycopy(
        "WEBPVP8X".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, anim, 8, 8);
    anim[16] = 10;
    anim[20] = 2;
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .file(new MockMultipartFile("archivo", "x.webp", "image/webp", anim))
                .param("orden", "0")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aceptaWebpComoEntrada() throws Exception {
    long id = celular(marca("ZTE"), "Axon", true, 1900), image = upload(id, 0);
    byte[] webp =
        mvc.perform(get("/api/media/" + image)).andReturn().getResponse().getContentAsByteArray();
    mvc.perform(
            multipart("/api/admin/celulares/" + id + "/imagenes")
                .file(new MockMultipartFile("archivo", "foto.webp", "image/webp", webp))
                .param("orden", "1")
                .with(httpBasic(USER, PASS))
                .header("X-Admin-Request", "true"))
        .andExpect(status().isCreated());
  }

  @Test
  void cargaConcurrenteRespetaMaximoDeOcho() throws Exception {
    long id = celular(marca("Nothing"), "Phone", true, 3000);
    var file = png(8, 8);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (int n = 0; n < 10; n++) {
        final int orden = n;
        futures.add(
            executor.submit(
                () ->
                    mvc.perform(
                            multipart("/api/admin/celulares/" + id + "/imagenes")
                                .file(file)
                                .param("orden", "" + orden)
                                .with(httpBasic(USER, PASS))
                                .header("X-Admin-Request", "true"))
                        .andReturn()
                        .getResponse()
                        .getStatus()));
      }
      var statuses = new ArrayList<Integer>();
      for (var future : futures)
        statuses.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(8, Collections.frequency(statuses, 201));
      assertEquals(2, Collections.frequency(statuses, 409));
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertEquals(8, imagenes.countByCelularId(id));
  }
}
