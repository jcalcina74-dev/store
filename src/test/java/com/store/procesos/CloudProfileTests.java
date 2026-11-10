package com.store.procesos;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.store.procesos.storage.CloudinaryImagenStorage;
import com.store.procesos.storage.ImagenStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("cloud")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "DB_URL=jdbc:h2:mem:cloud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "DB_USERNAME=sa", "DB_PASSWORD=",
    "ADMIN_USERNAME=cloudtest", "ADMIN_PASSWORD=Test-cloud-only-2026",
    "CLOUDINARY_CLOUD_NAME=demo", "CLOUDINARY_API_KEY=fake", "CLOUDINARY_API_SECRET=fake"
})
class CloudProfileTests {
  @Autowired ImagenStorage storage;
  @Autowired MockMvc mvc;

  @Test
  void iniciaConAlmacenamientoRemotoYHealthPublico() throws Exception {
    assertInstanceOf(CloudinaryImagenStorage.class, storage);
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }
}
