package com.store.procesos.storage;

import com.store.procesos.error.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.media-storage", havingValue = "local", matchIfMissing = true)
public class LocalImagenStorage implements ImagenStorage {
  private final Path root;

  public LocalImagenStorage(@Value("${app.media-dir}") String directory) throws IOException {
    root = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(root.resolve("celulares"));
    if (Files.isSymbolicLink(root) || Files.isSymbolicLink(root.resolve("celulares")))
      throw new IOException("El directorio media no puede ser un enlace simbolico");
  }

  private Path resolve(String relative) {
    if (!relative.matches("celulares/[a-f0-9-]{36}\\.webp"))
      throw new IllegalArgumentException("Ruta de imagen invalida");
    var path = root.resolve(relative).normalize();
    if (!path.startsWith(root) || Files.isSymbolicLink(path))
      throw new IllegalArgumentException("Ruta de imagen invalida");
    return path;
  }

  @Override
  public String guardar(byte[] bytes) {
    var name = "celulares/" + UUID.randomUUID() + ".webp";
    var path = resolve(name);
    try {
      Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
      return name;
    } catch (IOException e) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen");
    }
  }

  @Override
  public Resource leer(String path) {
    var file = resolve(path);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
      throw ApiException.notFound("Imagen");
    return new FileSystemResource(file);
  }

  @Override
  public void eliminar(String path) {
    try {
      Files.deleteIfExists(resolve(path));
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo eliminar una imagen", e);
    }
  }
}
