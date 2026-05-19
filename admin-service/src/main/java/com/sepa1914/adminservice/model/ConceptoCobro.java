package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Entidad ConceptoCobro.
 * Sincronizada con la tabla 'conceptos_cobro' de tu base de datos real.
 * Representa las cuotas o servicios que se cargan a los vecinos vinculados a la normativa contable.
 */
@Entity
@Table(name = "conceptos_cobro")
public class ConceptoCobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(precision = 10, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "mes_inicio", nullable = false)
    private Integer mesInicio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Periodicidad periodicidad = Periodicidad.MENSUAL;

    @Enumerated(EnumType.STRING)
    private TipoImpuesto tipoImpuesto = TipoImpuesto.EXENTO;

    @Column(precision = 10, scale = 2)
    private BigDecimal porcentajeImpuesto = BigDecimal.ZERO;

    // RELACIÓN: El vecino al que pertenece este concepto
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vecino_id")
    private Vecino vecino;

    // RELACIÓN: La comunidad a la que pertenece (Concepto Maestro)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id")
    private Comunidad comunidad;

    // --- NUEVA RELACIÓN PARA NORMALIZACIÓN CONTABLE ---
    // Permite que cada concepto (Cuota, Derrama, Fondo) apunte a su cuenta del Grupo 7 o Subgrupo 11
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_id")
    private CuentaContable cuentaContable;

    // VÍNCULO CON BANCO: Guarda el ID del movimiento bancario que originó este cargo (devoluciones)
    @Column(name = "movimiento_bancario_id")
    private Long movimientoBancarioId;

    public enum Periodicidad {
        MENSUAL, BIMESTRAL, TRIMESTRAL, CUATRIMESTRAL, SEMESTRAL, ANUAL
    }

    public ConceptoCobro() {}

    // --- MÉTODOS DE FUNCIONALIDAD Y LÓGICA DE NEGOCIO ---

    /**
     * Verifica si el concepto debe emitirse en un mes concreto.
     * No se elimina la funcionalidad original, se mantiene la lógica de periodicidad.
     */
    public boolean correspondeMes(int mes) {
        if (!activo) return false;
        if (periodicidad == Periodicidad.MENSUAL) return true;

        // Refactorización: Cálculo de correspondencia según mes de inicio y periodicidad
        int mesesIntervalo = switch (periodicidad) {
            case BIMESTRAL -> 2;
            case TRIMESTRAL -> 3;
            case CUATRIMESTRAL -> 4;
            case SEMESTRAL -> 6;
            case ANUAL -> 12;
            default -> 1;
        };

        return (mes >= mesInicio) && ((mes - mesInicio) % mesesIntervalo == 0);
    }

    // --- GETTERS Y SETTERS ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Integer getMesInicio() {
        return mesInicio;
    }

    public void setMesInicio(Integer mesInicio) {
        this.mesInicio = mesInicio;
    }

    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    public void setPeriodicidad(Periodicidad periodicidad) {
        this.periodicidad = periodicidad;
    }

    public Vecino getVecino() {
        return vecino;
    }

    public void setVecino(Vecino vecino) {
        this.vecino = vecino;
    }

    public Comunidad getComunidad() {
        return comunidad;
    }

    public void setComunidad(Comunidad comunidad) {
        this.comunidad = comunidad;
    }

    public CuentaContable getCuentaContable() {
        return cuentaContable;
    }

    public void setCuentaContable(CuentaContable cuentaContable) {
        this.cuentaContable = cuentaContable;
    }

    public Long getMovimientoBancarioId() {
        return movimientoBancarioId;
    }

    public void setMovimientoBancarioId(Long movimientoBancarioId) {
        this.movimientoBancarioId = movimientoBancarioId;
    }

    public TipoImpuesto getTipoImpuesto(){ return tipoImpuesto; }

    public void setTipoImpuesto(TipoImpuesto tipoImpuesto){ this.tipoImpuesto = tipoImpuesto;}

    public BigDecimal getPorcentajeImpuesto() { return porcentajeImpuesto; }

    public void setPorcentajeImpuesto(BigDecimal porcentajeImpuesto) { this.porcentajeImpuesto = porcentajeImpuesto; }
}