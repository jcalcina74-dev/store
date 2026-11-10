package com.store.procesos.storage;

import com.cloudinary.Cloudinary;
import com.store.procesos.error.ApiException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.media-storage", havingValue = "cloudinary")
public class CloudinaryImagenStorage implements ImagenStorage {
  private static final String PREFIX = "cloudinary:";
  private static final String FOLDER = "store/celulares/";
  private final Cloudinary cloudinary;
  private final CloudinaryDownload download;

  public CloudinaryImagenStorage(Cloudinary cloudinary, CloudinaryDownload download) {
    this.cloudinary = cloudinary;
    this.download = download;
  }

  @Override
  public String guardar(byte[] bytes) {
    String id = FOLDER + UUID.randomUUID();
    try {
      cloudinary.uploader().upload(bytes, Map.of(
          "public_id", id, "resource_type", "image", "type", "authenticated",
          "format", "webp", "overwrite", false));
      return PREFIX + id;
    } catch (Exception e) {
      // Un timeout puede ocurrir despues de que el proveedor guardo la imagen.
      // La clave UUID permite intentar compensar sin borrar otro archivo.
      try { eliminar(PREFIX + id); } catch (RuntimeException ignored) { }
      throw unavailable();
    }
  }

  @Override
  public Resource leer(String path) {
    String id = publicId(path);
    try {
      String url = cloudinary.privateDownload(id, "webp", Map.of(
          "resource_type", "image", "type", "authenticated",
          "expires_at", Instant.now().getEpochSecond() + 60, "attachment", false));
      return new ByteArrayResource(download.obtener(url));
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw unavailable();
    }
  }

  @Override
  public void eliminar(String path) {
    String id = publicId(path);
    try {
      var result = cloudinary.uploader().destroy(id, Map.of(
          "resource_type", "image", "type", "authenticated", "invalidate", true));
      if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result")))
        throw unavailable();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  private String publicId(String path) {
    if (path == null || !path.matches(
        "cloudinary:store/celulares/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
      throw ApiException.notFound("Imagen");
    return path.substring(PREFIX.length());
  }

  private ApiException unavailable() {
    return new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo acceder al almacenamiento de imagenes");
  }
}
