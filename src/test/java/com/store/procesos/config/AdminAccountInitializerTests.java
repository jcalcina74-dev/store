package com.store.procesos.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.store.procesos.model.Administrador;
import com.store.procesos.repository.AdministradorRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminAccountInitializerTests {
  private final AdministradorRepository repository = mock(AdministradorRepository.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

  private Administrador existing() {
    var account = new Administrador();
    account.setId(1L);
    account.setUsuario("original");
    account.setPasswordHash(encoder.encode("Original-password-2026"));
    when(repository.findById(1L)).thenReturn(Optional.of(account));
    return account;
  }

  private void initialize(String user, String password, boolean update) {
    new AdminAccountInitializer(repository, encoder, user, password, update)
        .run(new DefaultApplicationArguments());
  }

  @Test
  void createsTheSingleAccountWithHashedPassword() {
    when(repository.findById(1L)).thenReturn(Optional.empty());
    initialize("  creador  ", "Nueva-password-2026", false);
    var captor = org.mockito.ArgumentCaptor.forClass(Administrador.class);
    verify(repository).saveAndFlush(captor.capture());
    assertEquals(1L, captor.getValue().getId());
    assertEquals("creador", captor.getValue().getUsuario());
    assertTrue(encoder.matches("Nueva-password-2026", captor.getValue().getPasswordHash()));
  }

  @Test
  void normalRestartDoesNotChangeExistingCredentials() {
    var account = existing();
    String hash = account.getPasswordHash();
    initialize("diferente", "Otra-password-2026", false);
    verify(repository, never()).saveAndFlush(any());
    assertEquals("original", account.getUsuario());
    assertEquals(hash, account.getPasswordHash());
  }

  @Test
  void normalRestartDoesNotRequireBootstrapSecretsAgain() {
    var account = existing();
    initialize("", "", false);
    verify(repository, never()).saveAndFlush(any());
    assertEquals("original", account.getUsuario());
  }

  @Test
  void explicitUpdateRotatesCredentialsAndPreservesTheSingleId() {
    var account = existing();
    initialize("nuevo_usuario", "Nueva-password-2026", true);
    verify(repository).saveAndFlush(account);
    assertEquals(1L, account.getId());
    assertEquals("nuevo_usuario", account.getUsuario());
    assertTrue(encoder.matches("Nueva-password-2026", account.getPasswordHash()));
    assertFalse(encoder.matches("Original-password-2026", account.getPasswordHash()));
  }

  @Test
  void invalidUsernameCannotModifyTheExistingAccount() {
    var account = existing();
    String originalHash = account.getPasswordHash();
    for (String invalid : new String[]{" ", "invalido:usuario", "x".repeat(51), "nombre\nusuario"}) {
      assertThrows(IllegalStateException.class,
          () -> initialize(invalid, "Nueva-password-2026", true));
    }
    verify(repository, never()).saveAndFlush(any());
    assertEquals("original", account.getUsuario());
    assertEquals(originalHash, account.getPasswordHash());
  }

  @Test
  void invalidPasswordCannotModifyTheExistingAccount() {
    var account = existing();
    String originalHash = account.getPasswordHash();
    for (String invalid : new String[]{"corta", " ".repeat(20), "a".repeat(73), "\u00e1".repeat(37)}) {
      assertThrows(IllegalStateException.class,
          () -> initialize("nuevo", invalid, true));
    }
    verify(repository, never()).saveAndFlush(any());
    assertEquals(originalHash, account.getPasswordHash());
    assertEquals("original", account.getUsuario());
  }
}
