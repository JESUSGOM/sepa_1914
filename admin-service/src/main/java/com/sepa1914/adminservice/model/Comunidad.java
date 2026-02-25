package com.sepa1914.adminservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "comunidades")
public class Comunidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre del Acreedor (Atributo AT-03) - Máximo 70 caracteres
    @Column(length = 70, nullable = false)
    private String nombre;

    // Identificador del Acreedor (Atributo AT-02) - Unívoco en el esquema básico
    // Estructura: Código País (ES) + DC + Sufijo + NIF
    @Column(name = "identificador_acreedor", length = 35, nullable = false)
    private String identificadorAcreedor;

    // Cuenta de pago del Acreedor (Atributo AT-04) - Debe ser el IBAN
    @Column(length = 34, nullable = false)
    private String iban;

    // RELACIÓN ACTUALIZADA: Vinculación real con el Administrador
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario administrador;

    public Comunidad() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIdentificadorAcreedor() { return identificadorAcreedor; }
    public void setIdentificadorAcreedor(String identificadorAcreedor) { this.identificadorAcreedor = identificadorAcreedor; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public Usuario getAdministrador() { return administrador; }
    public void setAdministrador(Usuario administrador) { this.administrador = administrador; }
}