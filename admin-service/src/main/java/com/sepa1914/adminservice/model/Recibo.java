package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entidad que representa un recibo emitido a una propiedad (vecino_id).
 * Centraliza la información para el Balance de Situación y la Norma 43.
 * Refactorizado para soportar cobros parciales y conciliación masiva.
 * INCLUYE CAMPO CONCEPTO PARA DESGLOSE EN LIQUIDACIÓN ANUAL.
 */
@Entity
@Table(name = "contabilidad_recibos")
public class Recibo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fechaEmision;

    /**
     * NUEVO: Campo concepto para evitar errores de compilación en el Repository.
     * Almacena el texto descriptivo (ej: Cuota Ordinaria, Derrama, etc.)
     */
    @Column(name = "concepto", length = 255)
    private String concepto;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal importe;

    /**
     * Importe que ya ha sido abonado de este recibo.
     * Permite gestionar casos donde una transferencia no cubre el total.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal pagadoAcumulado = BigDecimal.ZERO;

    /**
     * Relación con la propiedad específica.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vecino_id", nullable = false)
    private Vecino vecino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    /**
     * Estado del ciclo de vida del recibo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRecibo estado = EstadoRecibo.PENDIENTE;

    @Column(name = "fecha_cobro_banco")
    private LocalDate fechaCobroBanco;

    /**
     * Relación con el apunte real de la Norma 43.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_bancario_id", unique = false)
    private MovimientoBancario movimientoBancario;

    public enum EstadoRecibo {
        PENDIENTE, COBRADO, DEVUELTO
    }

    public Recibo() {}

    // --- MÉTODOS DE CÁLCULO DE SALDOS ---

    /**
     * Calcula cuánto dinero queda por pagar de este recibo.
     * @return BigDecimal saldo restante.
     */
    public BigDecimal getSaldoPendiente() {
        if (this.importe == null) return BigDecimal.ZERO;
        BigDecimal yaPagado = (this.pagadoAcumulado != null) ? this.pagadoAcumulado : BigDecimal.ZERO;
        return this.importe.subtract(yaPagado);
    }

    /**
     * Registra un pago parcial o total.
     * Si el acumulado iguala al importe, el estado cambia automáticamente a COBRADO.
     */
    public void registrarPago(BigDecimal entrega) {
        if (entrega == null) return;
        if (this.pagadoAcumulado == null) this.pagadoAcumulado = BigDecimal.ZERO;

        this.pagadoAcumulado = this.pagadoAcumulado.add(entrega);

        if (this.pagadoAcumulado.compareTo(this.importe) >= 0) {
            this.estado = EstadoRecibo.COBRADO;
        }
    }

    // --- MÉTODOS DE COMPATIBILIDAD ---

    /**
     * Soporte para lógica de booleanos mantenida.
     */
    public boolean isCobrado() {
        return EstadoRecibo.COBRADO.equals(this.estado);
    }

    /**
     * Permite transiciones de estado mediante booleano.
     */
    public void setCobrado(boolean cobrado) {
        if (cobrado) {
            this.estado = EstadoRecibo.COBRADO;
            this.pagadoAcumulado = (this.importe != null) ? this.importe : BigDecimal.ZERO;
        } else {
            this.estado = EstadoRecibo.PENDIENTE;
            this.pagadoAcumulado = BigDecimal.ZERO;
        }
    }

    // --- GETTERS Y SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }

    public BigDecimal getPagadoAcumulado() { return pagadoAcumulado; }
    public void setPagadoAcumulado(BigDecimal pagadoAcumulado) { this.pagadoAcumulado = pagadoAcumulado; }

    public Vecino getVecino() { return vecino; }
    public void setVecino(Vecino vecino) { this.vecino = vecino; }

    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }

    public EstadoRecibo getEstado() { return estado; }
    public void setEstado(EstadoRecibo estado) { this.estado = estado; }

    public LocalDate getFechaCobroBanco() { return fechaCobroBanco; }
    public void setFechaCobroBanco(LocalDate fechaCobroBanco) { this.fechaCobroBanco = fechaCobroBanco; }

    public MovimientoBancario getMovimientoBancario() { return movimientoBancario; }
    public void setMovimientoBancario(MovimientoBancario movimientoBancario) {
        this.movimientoBancario = movimientoBancario;
    }
}