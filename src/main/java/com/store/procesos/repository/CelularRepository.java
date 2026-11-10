package com.store.procesos.repository;

import com.store.procesos.model.Celular;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface CelularRepository
    extends JpaRepository<Celular, Long>, JpaSpecificationExecutor<Celular> {
  boolean existsByMarcaId(Long marcaId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Celular c where c.id = :id")
  Optional<Celular> findLockedById(Long id);
}
