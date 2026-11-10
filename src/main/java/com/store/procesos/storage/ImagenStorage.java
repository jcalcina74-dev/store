package com.store.procesos.storage;

import org.springframework.core.io.Resource;

public interface ImagenStorage {
  String guardar(byte[] bytes);

  Resource leer(String ruta);

  void eliminar(String ruta);
}
