package com.store.procesos.repository;

import com.store.procesos.model.ImagenCelular;
import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenCelularRepository extends JpaRepository<ImagenCelular, Long> {
  @Override
  @EntityGraph(attributePaths = "celular")
  Optional<ImagenCelular> findById(Long id);

  List<ImagenCelular> findByCelularIdOrderByOrdenAsc(Long celularId);

  List<ImagenCelular> findByCelularIdInOrderByOrdenAsc(List<Long> ids);

  long countByCelularId(Long celularId);

  boolean existsByCelularIdAndOrden(Long celularId, Integer orden);

  Optional<ImagenCelular> findByIdAndCelularId(Long id, Long celularId);
}
