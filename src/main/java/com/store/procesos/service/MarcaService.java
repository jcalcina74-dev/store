package com.store.procesos.service;

import com.store.procesos.model.Marca;
import com.store.procesos.realtime.CatalogoNotificador;
import com.store.procesos.dto.*;
import com.store.procesos.error.ApiException;
import com.store.procesos.repository.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarcaService {
  private final MarcaRepository marcas;
  private final CelularRepository celulares;
  private final CatalogoNotificador notificador;

  public MarcaService(MarcaRepository marcas, CelularRepository celulares, CatalogoNotificador notificador) {
    this.marcas = marcas;
    this.celulares = celulares;
    this.notificador = notificador;
  }

  public List<MarcaResponse> listar() {
    return marcas.findAllByOrderByNombreAsc().stream()
        .map(m -> new MarcaResponse(m.getId(), m.getNombre()))
        .toList();
  }

  @Transactional
  public MarcaResponse crear(MarcaRequest dto) {
    var m = new Marca();
    m.setNombre(TextNormalizer.key(dto.nombre()));
    marcas.saveAndFlush(m);
    notificador.notificar();
    return new MarcaResponse(m.getId(), m.getNombre());
  }

  @Transactional
  public MarcaResponse actualizar(Long id, MarcaRequest dto) {
    var m = marcas.findLockedById(id).orElseThrow(() -> ApiException.notFound("Marca"));
    m.setNombre(TextNormalizer.key(dto.nombre()));
    marcas.flush();
    notificador.notificar();
    return new MarcaResponse(m.getId(), m.getNombre());
  }

  @Transactional
  public void eliminar(Long id) {
    var m = marcas.findLockedById(id).orElseThrow(() -> ApiException.notFound("Marca"));
    if (celulares.existsByMarcaId(id))
      throw ApiException.conflict("La marca tiene celulares asociados");
    marcas.delete(m);
    marcas.flush();
    notificador.notificar();
  }
}
