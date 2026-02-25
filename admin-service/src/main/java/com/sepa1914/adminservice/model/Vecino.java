package com.sepa1914.adminservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vecinos")
public class Vecino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 70)
    private String nombre;

    @Column(nullable = false, length = 34)
    private String iban; // AT-07 (IBAN del deudor)

    @Column(name = "referencia_mandato", unique = true, nullable = false, length = 35)
    private String referenciaMandato; // AT-01 (Referencia de la orden de domiciliación)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    // Constructor vacío (obligatorio para JPA)
    public Vecino() {}

    // Getters y Setters manuales
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public String getReferenciaMandato() { return referenciaMandato; }
    public void setReferenciaMandato(String referenciaMandato) { this.referenciaMandato = referenciaMandato; }

    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }
}