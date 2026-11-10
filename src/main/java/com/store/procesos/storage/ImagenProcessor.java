package com.store.procesos.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.store.procesos.error.ApiException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import javax.imageio.*;
import javax.imageio.stream.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImagenProcessor {
  public record Procesada(byte[] bytes, int ancho, int alto) {}

  public Procesada procesar(MultipartFile file) {
    if (file == null || file.isEmpty()) throw ApiException.badRequest("La imagen es obligatoria");
    if (file.getSize() > 5 * 1024 * 1024)
      throw new ApiException(
          org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Maximo 5 MiB por imagen");
    try {
      byte[] input = file.getBytes();
      rejectAnimation(input);
      BufferedImage source;
      try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw ApiException.badRequest("El archivo no es una imagen valida");
        var reader = readers.next();
        try {
          if (!Set.of("jpeg", "jpg", "png", "webp")
              .contains(reader.getFormatName().toLowerCase(Locale.ROOT)))
            throw ApiException.badRequest("Solo se admiten JPEG, PNG o WebP");
          reader.setInput(stream, false, false);
          int w = reader.getWidth(0), h = reader.getHeight(0);
          if (w < 1 || h < 1 || (long) w * h > 20_000_000)
            throw ApiException.badRequest("Maximo 20 megapixeles");
          var readParam = reader.getDefaultReadParam();
          int step = Math.max(1, Math.max(w, h) / 1600);
          readParam.setSourceSubsampling(step, step, 0, 0);
          source = reader.read(0, readParam);
        } finally {
          reader.dispose();
        }
      }
      if (source == null) throw ApiException.badRequest("No se pudo decodificar la imagen");
      source = orient(source, orientation(input));
      double scale = Math.min(1.0, 1600.0 / Math.max(source.getWidth(), source.getHeight()));
      int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
      int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
      var target =
          new BufferedImage(
              width,
              height,
              source.getColorModel().hasAlpha()
                  ? BufferedImage.TYPE_INT_ARGB
                  : BufferedImage.TYPE_INT_RGB);
      var graphics = target.createGraphics();
      try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, width, height, null);
      } finally {
        graphics.dispose();
      }
      var writers = ImageIO.getImageWritersByMIMEType("image/webp");
      if (!writers.hasNext()) throw new IllegalStateException("Codec WebP no disponible");
      var writer = writers.next();
      try (var output = new ByteArrayOutputStream();
          var stream = new MemoryCacheImageOutputStream(output)) {
        writer.setOutput(stream);
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        var types = params.getCompressionTypes();
        if (types != null) {
          String lossy =
              java.util.Arrays.stream(types)
                  .filter(t -> t.equalsIgnoreCase("Lossy"))
                  .findFirst()
                  .orElse(types[0]);
          params.setCompressionType(lossy);
        }
        params.setCompressionQuality(0.82f);
        writer.write(null, new IIOImage(target, null, null), params);
        stream.flush();
        return new Procesada(output.toByteArray(), width, height);
      } finally {
        writer.dispose();
      }
    } catch (ApiException e) {
      throw e;
    } catch (IOException | IllegalArgumentException e) {
      throw ApiException.badRequest("Imagen corrupta o formato no compatible");
    }
  }

  private int orientation(byte[] data) {
    try {
      var directory =
          ImageMetadataReader.readMetadata(new ByteArrayInputStream(data))
              .getFirstDirectoryOfType(ExifIFD0Directory.class);
      return directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)
          ? directory.getInt(ExifIFD0Directory.TAG_ORIENTATION)
          : 1;
    } catch (Exception e) {
      return 1;
    }
  }

  private BufferedImage orient(BufferedImage src, int orientation) {
    if (orientation < 2 || orientation > 8) return src;
    int w = src.getWidth(), h = src.getHeight();
    boolean swap = orientation >= 5;
    var dst =
        new BufferedImage(
            swap ? h : w,
            swap ? w : h,
            src.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int dx = x, dy = y;
        switch (orientation) {
          case 2 -> dx = w - 1 - x;
          case 3 -> {
            dx = w - 1 - x;
            dy = h - 1 - y;
          }
          case 4 -> dy = h - 1 - y;
          case 5 -> {
            dx = y;
            dy = x;
          }
          case 6 -> {
            dx = h - 1 - y;
            dy = x;
          }
          case 7 -> {
            dx = h - 1 - y;
            dy = w - 1 - x;
          }
          case 8 -> {
            dx = y;
            dy = w - 1 - x;
          }
          default -> {}
        }
        dst.setRGB(dx, dy, src.getRGB(x, y));
      }
    return dst;
  }

  private void rejectAnimation(byte[] bytes) {
    // PNG: rechazar acTL; WebP: rechazar ANIM/ANMF y bit de animacion VP8X.
    if (bytes.length >= 12
        && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
        && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
      for (int p = 12; p + 8 <= bytes.length; ) {
        String chunk = new String(bytes, p, 4, StandardCharsets.US_ASCII);
        long len = 0;
        for (int i = 0; i < 4; i++) len |= (long) (bytes[p + 4 + i] & 255) << (8 * i);
        if (chunk.equals("ANIM")
            || chunk.equals("ANMF")
            || (chunk.equals("VP8X") && len > 0 && p + 8 < bytes.length && (bytes[p + 8] & 2) != 0))
          throw ApiException.badRequest("No se admiten imagenes animadas");
        long next = (long) p + 8 + len + (len % 2);
        if (next > bytes.length || next <= p) throw ApiException.badRequest("WebP corrupto");
        p = (int) next;
      }
    } else if (bytes.length >= 8
        && bytes[0] == (byte) 137
        && bytes[1] == 80
        && bytes[2] == 78
        && bytes[3] == 71) {
      for (int p = 8; p + 12 <= bytes.length; ) {
        long len = 0;
        for (int i = 0; i < 4; i++) len = (len << 8) | (bytes[p + i] & 255);
        if (new String(bytes, p + 4, 4, StandardCharsets.US_ASCII).equals("acTL"))
          throw ApiException.badRequest("No se admiten imagenes animadas");
        long next = (long) p + 12 + len;
        if (next > bytes.length || next <= p) throw ApiException.badRequest("PNG corrupto");
        p = (int) next;
      }
    }
  }
}
