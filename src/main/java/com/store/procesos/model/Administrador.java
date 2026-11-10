package com.store.procesos.model;

import jakarta.persistence.*;

@Entity
@Table(name = "\"T_Administrador\"")
public class Administrador {
  @Id private Long id;

  @Column(nullable = false, length = 50, unique = true)
  private String usuario;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsuario() {
    return usuario;
  }

  public void setUsuario(String usuario) {
    this.usuario = usuario;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
