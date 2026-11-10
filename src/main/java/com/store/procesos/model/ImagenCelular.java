package com.store.procesos.model;

import jakarta.persistence.*;

@Entity
@Table(name = "\"T_ImagenCelular\"")
public class ImagenCelular {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "celular_id")
  private Celular celular;

  @Column(name = "ruta_relativa", nullable = false, length = 255, unique = true)
  private String rutaRelativa;

  @Column(name = "mime_type", nullable = false, length = 50)
  private String mimeType;

  @Column(name = "tamanio_bytes", nullable = false)
  private Long tamanioBytes;

  @Column(nullable = false)
  private Integer ancho;

  @Column(nullable = false)
  private Integer alto;

  @Column(nullable = false)
  private Integer orden;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Celular getCelular() {
    return celular;
  }

  public void setCelular(Celular celular) {
    this.celular = celular;
  }

  public String getRutaRelativa() {
    return rutaRelativa;
  }

  public void setRutaRelativa(String rutaRelativa) {
    this.rutaRelativa = rutaRelativa;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public Long getTamanioBytes() {
    return tamanioBytes;
  }

  public void setTamanioBytes(Long tamanioBytes) {
    this.tamanioBytes = tamanioBytes;
  }

  public Integer getAncho() {
    return ancho;
  }

  public void setAncho(Integer ancho) {
    this.ancho = ancho;
  }

  public Integer getAlto() {
    return alto;
  }

  public void setAlto(Integer alto) {
    this.alto = alto;
  }

  public Integer getOrden() {
    return orden;
  }

  public void setOrden(Integer orden) {
    this.orden = orden;
  }
}
