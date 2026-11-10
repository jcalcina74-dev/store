package com.store.procesos.repository;

import com.store.procesos.model.Administrador;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
  Optional<Administrador> findByUsuario(String usuario);
}
