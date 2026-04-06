package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * Entidad que representa una Cuenta Contable dentro del sistema.
 * Mapeada a la tabla 'contabilidad_cuentas'.
 */
@Entity
@Table(name = "contabilidad_cuentas")
public class CuentaContable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    private TipoCuenta tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    /**
     * Constructor por defecto requerido por JPA.
     */
    public CuentaContable() {
    }

    /**
     * Constructor completo para facilitar la creación de instancias.
     */
    public CuentaContable(String codigo, String nombre, TipoCuenta tipo, Comunidad comunidad) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.tipo = tipo;
        this.comunidad = comunidad;
    }

    // ==========================================
    // GETTERS Y SETTERS (Estándar JavaBeans)
    // ==========================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoCuenta getTipo() {
        return tipo;
    }

    public void setTipo(TipoCuenta tipo) {
        this.tipo = tipo;
    }

    public Comunidad getComunidad() {
        return comunidad;
    }

    public void setComunidad(Comunidad comunidad) {
        this.comunidad = comunidad;
    }

    // ==========================================
    // MÉTODOS DE OBJETO (EQUALS, HASHCODE, TOSTRING)
    // ==========================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CuentaContable that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (codigo != null ? codigo : "S/C") + " - " + nombre;
    }
}