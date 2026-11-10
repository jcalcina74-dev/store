package com.store.procesos.config;

import com.store.procesos.model.Administrador;
import com.store.procesos.repository.AdministradorRepository;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Aprovisionamiento privado del servidor; no se expone por HTTP. */
@Component
public class AdminAccountInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
  private final AdministradorRepository repository;
  private final PasswordEncoder encoder;
  private final String username;
  private final String password;
  private final boolean updateOnStart;

  public AdminAccountInitializer(
      AdministradorRepository repository,
      PasswordEncoder encoder,
      @Value("${app.admin.username}") String username,
      @Value("${app.admin.password}") String password,
      @Value("${app.admin.update-on-start:false}") boolean updateOnStart) {
    this.repository = repository;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
    this.updateOnStart = updateOnStart;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    var existing = repository.findById(1L);
    if (existing.isPresent() && !updateOnStart) return;

    String normalizedUsername = username == null ? "" : username.strip();
    if (normalizedUsername.isBlank() || normalizedUsername.length() > 50
        || normalizedUsername.contains(":") || normalizedUsername.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalStateException(
          "ADMIN_USERNAME debe tener entre 1 y 50 caracteres, sin ':' ni caracteres de control");
    }
    if (password == null || password.isBlank() || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalStateException(
          "ADMIN_PASSWORD debe tener al menos 12 caracteres y como maximo 72 bytes UTF-8");
    }

    var account = existing.orElseGet(Administrador::new);
    account.setId(1L);
    account.setUsuario(normalizedUsername);
    account.setPasswordHash(encoder.encode(password));
    repository.saveAndFlush(account);
    log.info(existing.isPresent()
        ? "Cuenta administradora actualizada desde la configuracion privada del servidor"
        : "Cuenta administradora inicializada");
  }
}
