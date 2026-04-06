package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "contabilidad_movimientos")
public class MovimientoContable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private String concepto;

    @Column(name = "numero_asiento", nullable = false)
    private String numeroAsiento; // UID para agrupar movimientos del mismo asiento

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaContable cuenta;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal debe = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal haber = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    public MovimientoContable() {}

    // --- GETTERS Y SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public String getNumeroAsiento() { return numeroAsiento; }
    public void setNumeroAsiento(String numeroAsiento) { this.numeroAsiento = numeroAsiento; }

    public CuentaContable getCuenta() { return cuenta; }
    public void setCuenta(CuentaContable cuenta) { this.cuenta = cuenta; }

    public BigDecimal getDebe() { return debe; }
    public void setDebe(BigDecimal debe) {
        this.debe = (debe != null) ? debe : BigDecimal.ZERO;
    }

    public BigDecimal getHaber() { return haber; }
    public void setHaber(BigDecimal haber) {
        this.haber = (haber != null) ? haber : BigDecimal.ZERO;
    }

    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }
}