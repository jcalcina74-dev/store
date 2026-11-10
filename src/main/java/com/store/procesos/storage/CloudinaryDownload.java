package com.store.procesos.storage;

import com.store.procesos.error.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.media-storage", havingValue = "cloudinary")
public class CloudinaryDownload {
  static final int MAX_BYTES = 10 * 1024 * 1024;
  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

  public byte[] obtener(String url) {
    try {
      URI uri = URI.create(url);
      // URL creada por el SDK; validar tambien cada redireccion antes de seguirla.
      for (int redirects = 0; redirects <= 3; redirects++) {
        validar(uri);
        var response = client.send(HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20)).GET().build(),
            info -> new LimitedBodySubscriber(MAX_BYTES));
        int status = response.statusCode();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
          uri = uri.resolve(response.headers().firstValue("Location").orElseThrow(IOException::new));
          continue;
        }
        if (status == 404) throw ApiException.notFound("Imagen");
        if (status != 200 || !response.headers().firstValue("Content-Type")
            .orElse("").split(";")[0].strip().equalsIgnoreCase("image/webp"))
          throw new IOException();
        byte[] bytes = response.body();
        if (bytes.length < 12 || bytes[0] != 'R' || bytes[1] != 'I'
            || bytes[2] != 'F' || bytes[3] != 'F' || bytes[8] != 'W'
            || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P')
          throw new IOException();
        return bytes;
      }
      throw new IOException();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (IOException | IllegalArgumentException e) {
      throw unavailable();
    }
  }

  private static void validar(URI uri) throws IOException {
    if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
        || !List.of("api.cloudinary.com", "res.cloudinary.com").contains(uri.getHost()))
      throw new IOException();
  }

  private static ApiException unavailable() {
    return new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo descargar la imagen");
  }

  // Acota memoria y conserva el timeout de HttpClient hasta recibir todo el cuerpo.
  static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private final long limit;
    private long received;
    private Flow.Subscription subscription;
    LimitedBodySubscriber(long limit) { this.limit = limit; }
    public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      delegate.onSubscribe(subscription);
    }
    public void onNext(List<ByteBuffer> items) {
      for (var item : items) received += item.remaining();
      if (received > limit) {
        subscription.cancel();
        delegate.onError(new IOException("Respuesta de imagen demasiado grande"));
      } else delegate.onNext(items);
    }
    public void onError(Throwable error) { delegate.onError(error); }
    public void onComplete() { delegate.onComplete(); }
  }
}
