package com.store.procesos.repository;

import com.store.procesos.model.Marca;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MarcaRepository extends JpaRepository<Marca, Long> {
  List<Marca> findAllByOrderByNombreAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Marca m where m.id = :id")
  Optional<Marca> findLockedById(Long id);
}
