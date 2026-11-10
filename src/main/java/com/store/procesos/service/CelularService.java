package com.store.procesos.service;

import com.store.procesos.model.*;
import com.store.procesos.realtime.CatalogoNotificador;
import com.store.procesos.dto.*;
import com.store.procesos.error.ApiException;
import com.store.procesos.repository.*;
import com.store.procesos.storage.*;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class CelularService {
  private final CelularRepository celulares;
  private final MarcaRepository marcas;
  private final ImagenCelularRepository imagenes;
  private final ImagenStorage storage;
  private final ImagenProcessor processor;
  private final FileLifecycle lifecycle;
  private final CatalogoNotificador notificador;

  public CelularService(
      CelularRepository celulares,
      MarcaRepository marcas,
      ImagenCelularRepository imagenes,
      ImagenStorage storage,
      ImagenProcessor processor,
      FileLifecycle lifecycle, CatalogoNotificador notificador) {
    this.celulares = celulares;
    this.marcas = marcas;
    this.imagenes = imagenes;
    this.storage = storage;
    this.processor = processor;
    this.lifecycle = lifecycle;
    this.notificador = notificador;
  }

  public PaginaResponse<CelularResponse> listar(
      boolean publico,
      Long marcaId,
      String texto,
      BigDecimal min,
      BigDecimal max,
      int page,
      int size,
      String sort) {
    if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE)
      throw ApiException.badRequest("page debe ser >= 0 y size debe estar entre 1 y 50");
    if ((min != null && min.signum() < 0)
        || (max != null && max.signum() < 0)
        || (min != null && max != null && min.compareTo(max) > 0))
      throw ApiException.badRequest("Rango de precios invalido");
    if (marcaId != null && marcaId < 1) throw ApiException.badRequest("marcaId invalido");
    if (texto != null && texto.length() > 120)
      throw ApiException.badRequest("texto admite como maximo 120 caracteres");
    var parts = sort.split(",", -1);
    if (parts.length != 2
        || !Set.of("id", "precio", "modelo").contains(parts[0])
        || !Set.of("asc", "desc").contains(parts[1]))
      throw ApiException.badRequest("sort debe ser id,asc|desc, precio,asc|desc o modelo,asc|desc");
    var order = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
    if (!parts[0].equals("id")) order = order.and(Sort.by(Sort.Direction.DESC, "id"));
    Specification<Celular> spec =
        (root, query, cb) -> {
          var predicates = new ArrayList<Predicate>();
          if (publico) predicates.add(cb.isTrue(root.get("activo")));
          if (marcaId != null) predicates.add(cb.equal(root.get("marca").get("id"), marcaId));
          if (min != null) predicates.add(cb.greaterThanOrEqualTo(root.get("precio"), min));
          if (max != null) predicates.add(cb.lessThanOrEqualTo(root.get("precio"), max));
          if (texto != null && !texto.isBlank()) {
            String escaped =
                TextNormalizer.key(texto).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            predicates.add(cb.like(cb.lower(root.get("modelo")), "%" + escaped + "%", '!'));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    var result = celulares.findAll(spec, PageRequest.of(page, size, order));
    var ids = result.getContent().stream().map(Celular::getId).toList();
    Map<Long, List<ImagenCelular>> grouped =
        ids.isEmpty()
            ? Map.of()
            : imagenes.findByCelularIdInOrderByOrdenAsc(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getCelular().getId()));
    var content =
        result.getContent().stream()
            .map(c -> response(c, grouped.getOrDefault(c.getId(), List.of())))
            .toList();
    return new PaginaResponse<>(
        content, page, size, result.getTotalElements(), result.getTotalPages());
  }

  public CelularResponse obtener(Long id, boolean publico) {
    var c = find(id);
    if (publico && !c.getActivo()) throw ApiException.notFound("Celular");
    return response(c, imagenes.findByCelularIdOrderByOrdenAsc(id));
  }

  @Transactional
  public CelularResponse crear(CelularRequest dto) {
    var c = new Celular();
    apply(c, dto);
    celulares.saveAndFlush(c);
    notificador.notificar();
    return response(c, List.of());
  }

  @Transactional
  public CelularResponse actualizar(Long id, CelularRequest dto) {
    var c = locked(id);
    apply(c, dto);
    celulares.flush();
    notificador.notificar();
    return response(c, imagenes.findByCelularIdOrderByOrdenAsc(id));
  }

  @Transactional
  public void eliminar(Long id) {
    var c = locked(id);
    var files = imagenes.findByCelularIdOrderByOrdenAsc(id);
    imagenes.deleteAll(files);
    imagenes.flush();
    celulares.delete(c);
    celulares.flush();
    notificador.notificar();
    files.forEach(i -> lifecycle.deleteAfterCommit(i.getRutaRelativa()));
  }

  @Transactional
  public ImagenResponse agregarImagen(Long id, MultipartFile archivo, int orden) {
    if (orden < 0) throw ApiException.badRequest("orden debe ser >= 0");
    var processed = processor.procesar(archivo);
    var c = locked(id);
    if (imagenes.countByCelularId(id) >= 8)
      throw ApiException.conflict("Maximo 8 imagenes por celular");
    if (imagenes.existsByCelularIdAndOrden(id, orden))
      throw ApiException.conflict("El orden ya esta ocupado");
    var path = storage.guardar(processed.bytes());
    lifecycle.deleteOnRollback(path);
    var i = new ImagenCelular();
    i.setCelular(c);
    i.setRutaRelativa(path);
    i.setMimeType("image/webp");
    i.setTamanioBytes((long) processed.bytes().length);
    i.setAncho(processed.ancho());
    i.setAlto(processed.alto());
    i.setOrden(orden);
    imagenes.saveAndFlush(i);
    notificador.notificar();
    return imageResponse(i);
  }

  @Transactional
  public void eliminarImagen(Long id, Long imagenId) {
    locked(id);
    var i =
        imagenes
            .findByIdAndCelularId(imagenId, id)
            .orElseThrow(() -> ApiException.notFound("Imagen"));
    imagenes.delete(i);
    imagenes.flush();
    lifecycle.deleteAfterCommit(i.getRutaRelativa());
    notificador.notificar();
  }

  private Celular find(Long id) {
    return celulares.findById(id).orElseThrow(() -> ApiException.notFound("Celular"));
  }

  private Celular locked(Long id) {
    return celulares.findLockedById(id).orElseThrow(() -> ApiException.notFound("Celular"));
  }

  private void apply(Celular c, CelularRequest d) {
    c.setMarca(
        marcas.findLockedById(d.marcaId()).orElseThrow(() -> ApiException.notFound("Marca")));
    c.setModelo(TextNormalizer.key(d.modelo()));
    c.setDescripcion(d.descripcion() == null ? null : TextNormalizer.display(d.descripcion()));
    c.setPrecio(d.precio());
    c.setStock(d.stock());
    c.setRamGb(d.ramGb());
    c.setAlmacenamientoGb(d.almacenamientoGb());
    c.setPantallaPulgadas(d.pantallaPulgadas());
    c.setBateriaMah(d.bateriaMah());
    c.setSistemaOperativo(TextNormalizer.display(d.sistemaOperativo()));
    c.setColor(TextNormalizer.key(d.color()));
    c.setActivo(d.activo());
  }

  private CelularResponse response(Celular c, List<ImagenCelular> files) {
    return new CelularResponse(
        c.getId(),
        new MarcaResponse(c.getMarca().getId(), c.getMarca().getNombre()),
        c.getModelo(),
        c.getDescripcion(),
        c.getPrecio(),
        "BOB",
        c.getStock(),
        c.getRamGb(),
        c.getAlmacenamientoGb(),
        c.getPantallaPulgadas(),
        c.getBateriaMah(),
        c.getSistemaOperativo(),
        c.getColor(),
        c.getActivo(),
        files.stream().map(this::imageResponse).toList());
  }

  private ImagenResponse imageResponse(ImagenCelular i) {
    return new ImagenResponse(
        i.getId(),
        "/api/media/" + i.getId(),
        i.getOrden(),
        i.getAncho(),
        i.getAlto(),
        i.getTamanioBytes());
  }
}
