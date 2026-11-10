package com.store.procesos.realtime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class CatalogoNotificador {
  private final ApplicationEventPublisher events;

  public CatalogoNotificador(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void notificar() {
    events.publishEvent(new CatalogoCambiado());
  }
}