package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "presupuestos", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"comunidad_id", "cuenta_id", "anio"})
})
public class Presupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaContable cuenta;

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal importe;

    /**
     * Constructor vacío requerido por JPA/Hibernate.
     */
    public Presupuesto() {
    }

    /**
     * Constructor para inicializar rápidamente un presupuesto.
     */
    public Presupuesto(Comunidad comunidad, CuentaContable cuenta, int anio, BigDecimal importe) {
        this.comunidad = comunidad;
        this.cuenta = cuenta;
        this.anio = anio;
        this.importe = importe;
    }

    // ==========================================
    // GETTERS Y SETTERS
    // ==========================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Comunidad getComunidad() {
        return comunidad;
    }

    public void setComunidad(Comunidad comunidad) {
        this.comunidad = comunidad;
    }

    public CuentaContable getCuenta() {
        return cuenta;
    }

    public void setCuenta(CuentaContable cuenta) {
        this.cuenta = cuenta;
    }

    public int getAnio() {
        return anio;
    }

    public void setAnio(int anio) {
        this.anio = anio;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }
}
