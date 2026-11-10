package com.store.procesos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "\"T_Celular\"")
public class Celular {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "marca_id")
  private Marca marca;

  @Column(nullable = false, length = 120)
  private String modelo;

  @Column(columnDefinition = "text")
  private String descripcion;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal precio;

  @Column(nullable = false)
  private Integer stock;

  @Column(name = "ram_gb", nullable = false)
  private Integer ramGb;

  @Column(name = "almacenamiento_gb", nullable = false)
  private Integer almacenamientoGb;

  @Column(name = "pantalla_pulgadas", nullable = false, precision = 4, scale = 2)
  private BigDecimal pantallaPulgadas;

  @Column(name = "bateria_mah", nullable = false)
  private Integer bateriaMah;

  @Column(name = "sistema_operativo", nullable = false, length = 80)
  private String sistemaOperativo;

  @Column(nullable = false, length = 50)
  private String color;

  @Column(nullable = false)
  private Boolean activo;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Marca getMarca() {
    return marca;
  }

  public void setMarca(Marca marca) {
    this.marca = marca;
  }

  public String getModelo() {
    return modelo;
  }

  public void setModelo(String modelo) {
    this.modelo = modelo;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public BigDecimal getPrecio() {
    return precio;
  }

  public void setPrecio(BigDecimal precio) {
    this.precio = precio;
  }

  public Integer getStock() {
    return stock;
  }

  public void setStock(Integer stock) {
    this.stock = stock;
  }

  public Integer getRamGb() {
    return ramGb;
  }

  public void setRamGb(Integer ramGb) {
    this.ramGb = ramGb;
  }

  public Integer getAlmacenamientoGb() {
    return almacenamientoGb;
  }

  public void setAlmacenamientoGb(Integer almacenamientoGb) {
    this.almacenamientoGb = almacenamientoGb;
  }

  public BigDecimal getPantallaPulgadas() {
    return pantallaPulgadas;
  }

  public void setPantallaPulgadas(BigDecimal pantallaPulgadas) {
    this.pantallaPulgadas = pantallaPulgadas;
  }

  public Integer getBateriaMah() {
    return bateriaMah;
  }

  public void setBateriaMah(Integer bateriaMah) {
    this.bateriaMah = bateriaMah;
  }

  public String getSistemaOperativo() {
    return sistemaOperativo;
  }

  public void setSistemaOperativo(String sistemaOperativo) {
    this.sistemaOperativo = sistemaOperativo;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public Boolean getActivo() {
    return activo;
  }

  public void setActivo(Boolean activo) {
    this.activo = activo;
  }
}
