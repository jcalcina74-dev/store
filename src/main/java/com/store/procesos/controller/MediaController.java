package com.store.procesos.controller;

import com.store.procesos.error.ApiException;
import com.store.procesos.repository.ImagenCelularRepository;
import com.store.procesos.storage.ImagenStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/media")
@Tag(name = "Media")
public class MediaController {
  private final ImagenCelularRepository imagenes;
  private final ImagenStorage storage;

  public MediaController(ImagenCelularRepository imagenes, ImagenStorage storage) {
    this.imagenes = imagenes;
    this.storage = storage;
  }

  @GetMapping(value = "/{imagenId}", produces = "image/webp")
  @Transactional(readOnly = true)
  @Operation(summary = "Obtener imagen WebP; inactivos visibles solo para ADMIN")
  public ResponseEntity<Resource> obtener(
      @PathVariable Long imagenId, Authentication auth, WebRequest request) throws IOException {
    var image = imagenes.findById(imagenId).orElseThrow(() -> ApiException.notFound("Imagen"));
    boolean admin =
        auth != null
            && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    if (!image.getCelular().getActivo() && !admin) throw ApiException.notFound("Imagen");

    var etag = "\"" + image.getId() + "-" + image.getTamanioBytes() + "\"";
    if (request.checkNotModified(etag))
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
    var resource = storage.leer(image.getRutaRelativa());
    // Revalidar en cada uso: al desactivar un celular deja de ser publico.
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("image/webp"))
        .contentLength(resource.contentLength())
        .eTag(etag)
        .cacheControl(CacheControl.noCache().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .body(resource);
  }
}
