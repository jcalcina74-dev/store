package com.store.procesos.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.store.procesos.error.ApiException;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class CloudinaryImagenStorageTests {
  private final Cloudinary cloudinary = spy(new Cloudinary(Map.of(
      "cloud_name", "demo", "api_key", "123", "api_secret", "fake-secret")));
  private final CloudinaryDownload download = mock(CloudinaryDownload.class);
  private final CloudinaryImagenStorage storage = new CloudinaryImagenStorage(cloudinary, download);
  private final Uploader uploader = mock(Uploader.class);

  @Test
  void conservaElIdentificadorPrivadoParaLecturaYBorrado() throws Exception {
    doReturn(uploader).when(cloudinary).uploader();
    byte[] bytes = {1, 2, 3};
    var upload = ArgumentCaptor.forClass(Map.class);
    String key = storage.guardar(bytes);
    verify(uploader).upload(same(bytes), upload.capture());
    String id = (String) upload.getValue().get("public_id");
    assertEquals("cloudinary:" + id, key);
    assertEquals("authenticated", upload.getValue().get("type"));
    assertEquals("webp", upload.getValue().get("format"));
    assertEquals(false, upload.getValue().get("overwrite"));

    when(download.obtener(anyString())).thenReturn(bytes);
    assertArrayEquals(bytes, storage.leer(key).getContentAsByteArray());
    var signedUrl = ArgumentCaptor.forClass(String.class);
    verify(download).obtener(signedUrl.capture());
    URI uri = URI.create(signedUrl.getValue());
    assertEquals("https", uri.getScheme());
    assertEquals("api.cloudinary.com", uri.getHost());
    assertTrue(uri.getQuery().contains("type=authenticated"));
    assertTrue(uri.getQuery().contains("signature="));
    assertTrue(uri.getQuery().contains("expires_at="));

    var destroy = ArgumentCaptor.forClass(Map.class);
    when(uploader.destroy(eq(id), anyMap())).thenReturn(Map.of("result", "not found"));
    storage.eliminar(key);
    verify(uploader).destroy(eq(id), destroy.capture());
    assertEquals("authenticated", destroy.getValue().get("type"));
  }

  @Test
  void fallaSinExponerDetallesDelProveedor() throws Exception {
    doReturn(uploader).when(cloudinary).uploader();
    when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("secret-test"));
    when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

    var error = assertThrows(ApiException.class, () -> storage.guardar(new byte[] {1}));
    assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
    assertFalse(error.toString().contains("secret-test"));
    assertNull(error.getCause());
    verify(uploader).destroy(startsWith("store/celulares/"), anyMap());
  }

  @Test
  void noInterpretaRutasLocalesComoRecursosRemotos() {
    assertEquals(HttpStatus.NOT_FOUND,
        assertThrows(ApiException.class, () -> storage.leer("celulares/local.webp")).getStatus());
    verifyNoInteractions(download);
  }
}
