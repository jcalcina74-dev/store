package com.store.procesos.realtime;

import com.store.procesos.error.ApiException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CatalogoEventStream {
  public record Aviso(String version) {}

  private final ConcurrentMap<UUID, Cliente> clientes = new ConcurrentHashMap<>();
  private final AtomicLong revision = new AtomicLong();
  private final String instancia = UUID.randomUUID().toString();
  private final ThreadPoolExecutor writers;
  private final int maxClients;
  private final long timeoutMs;
  private final long heartbeatNanos;
  private volatile boolean closed;

  public CatalogoEventStream(
      @Value("${app.sse.max-clients:100}") int maxClients,
      @Value("${app.sse.timeout-ms:300000}") long timeoutMs,
      @Value("${app.sse.heartbeat-ms:15000}") long heartbeatMs) {
    if (maxClients < 1 || timeoutMs < 1000 || heartbeatMs < 100 || heartbeatMs >= timeoutMs) {
      throw new IllegalArgumentException("Configuracion SSE invalida: limites positivos y heartbeat menor al timeout");
    }
    this.maxClients = maxClients;
    this.timeoutMs = timeoutMs;
    this.heartbeatNanos = TimeUnit.MILLISECONDS.toNanos(heartbeatMs);
    AtomicInteger threadNumber = new AtomicInteger();
    this.writers = new ThreadPoolExecutor(
        2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(maxClients),
        task -> {
          Thread thread = new Thread(task, "catalogo-sse-" + threadNumber.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        },
        new ThreadPoolExecutor.AbortPolicy());
  }

  public synchronized SseEmitter suscribir() {
    if (closed || clientes.size() >= maxClients) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Canal de eventos ocupado; vuelve a conectar mas tarde");
    }
    var id = UUID.randomUUID();
    var emitter = new SseEmitter(timeoutMs);
    var cliente = new Cliente(id, emitter);
    emitter.onCompletion(() -> clientes.remove(id, cliente));
    emitter.onError(error -> clientes.remove(id, cliente));
    emitter.onTimeout(() -> {
      clientes.remove(id, cliente);
      emitter.complete();
    });
    clientes.put(id, cliente);
    encolar(cliente);
    return emitter;
  }

  /** El commit solo cambia un contador en memoria: no espera escrituras de red. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void confirmado(CatalogoCambiado cambio) {
    revision.incrementAndGet();
  }

  /** Agrupa cambios rapidos. No consulta la base de datos. */
  @Scheduled(fixedDelayString = "${app.sse.dispatch-ms:250}")
  public void despachar() {
    if (closed) return;
    long now = System.nanoTime();
    long current = revision.get();
    for (var cliente : clientes.values()) {
      if (cliente.enviada != current || now - cliente.ultimoEnvio >= heartbeatNanos) {
        encolar(cliente);
      }
    }
  }

  private void encolar(Cliente cliente) {
    if (!cliente.enviando.compareAndSet(false, true)) return;
    try {
      writers.execute(() -> enviar(cliente));
    } catch (RejectedExecutionException ignored) {
      // Cola acotada: el proximo ciclo vuelve a intentar con la revision mas reciente.
      cliente.enviando.set(false);
    }
  }

  private void enviar(Cliente cliente) {
    try {
      if (closed || clientes.get(cliente.id) != cliente) return;
      long current = revision.get();
      if (cliente.enviada != current) {
        String version = instancia + ":" + current;
        String event = cliente.enviada < 0 ? "sincronizar" : "catalogo-actualizado";
        cliente.emitter.send(SseEmitter.event()
            .name(event).id(version).reconnectTime(3000)
            .data(new Aviso(version), MediaType.APPLICATION_JSON));
        cliente.enviada = current;
      } else {
        cliente.emitter.send(SseEmitter.event().comment("keep-alive"));
      }
      cliente.ultimoEnvio = System.nanoTime();
    } catch (IOException | IllegalStateException disconnected) {
      // Spring/Servlet completa la respuesta tras un error de escritura.
      clientes.remove(cliente.id, cliente);
    } finally {
      cliente.enviando.set(false);
    }
  }

  @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
  @PreDestroy
  public synchronized void cerrar() {
    if (closed) return;
    closed = true;
    for (var cliente : clientes.values()) {
      cliente.emitter.complete();
    }
    clientes.clear();
    writers.shutdownNow();
  }

  private static final class Cliente {
    private final UUID id;
    private final SseEmitter emitter;
    private final AtomicBoolean enviando = new AtomicBoolean();
    private volatile long enviada = -1;
    private volatile long ultimoEnvio = System.nanoTime();

    private Cliente(UUID id, SseEmitter emitter) {
      this.id = id;
      this.emitter = emitter;
    }
  }
}