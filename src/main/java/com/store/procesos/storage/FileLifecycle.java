package com.store.procesos.storage;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
public class FileLifecycle {
  private static final Logger log = LoggerFactory.getLogger(FileLifecycle.class);
  private final ImagenStorage storage;
  private final Set<String> pending = ConcurrentHashMap.newKeySet();

  public FileLifecycle(ImagenStorage storage) {
    this.storage = storage;
  }

  public void deleteOnRollback(String path) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) tryDelete(path);
          }
        });
  }

  public void deleteAfterCommit(String path) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            tryDelete(path);
          }
        });
  }

  private void tryDelete(String path) {
    try {
      storage.eliminar(path);
      pending.remove(path);
    } catch (RuntimeException e) {
      pending.add(path);
      log.warn("Limpieza de archivo pendiente; se reintentara", e);
    }
  }

  @Scheduled(fixedDelay = 60000)
  public void retry() {
    Set.copyOf(pending).forEach(this::tryDelete);
  }
}
