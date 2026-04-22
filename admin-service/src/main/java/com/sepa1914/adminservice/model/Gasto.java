package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad que representa una Factura Recibida o Gasto de la Comunidad.
 * Refactorizada para soportar ciclos de vida de pago, conciliación y vínculo contable.
 */
@Entity
@Table(name = "contabilidad_gastos")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_factura", nullable = false)
    private LocalDate fecha;

    @Column(name = "numero_factura", nullable = false)
    private String numeroFactura;

    @Column(nullable = false)
    private String proveedor;

    @Column(nullable = false, length = 500)
    private String concepto;

    @Column(name = "importe_total", precision = 19, scale = 2, nullable = false)
    private BigDecimal importeTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_gasto_id", nullable = false)
    private CuentaContable cuentaGasto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    @Column(name = "numero_asiento")
    private String numeroAsiento;

    @Column(nullable = false)
    private boolean pagado = false;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "ruta_pdf")
    private String rutaPdf;

    public Gasto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public void setNumeroFactura(String numeroFactura) {
        this.numeroFactura = numeroFactura;
    }

    public String getProveedor() {
        return proveedor;
    }

    public void setProveedor(String proveedor) {
        this.proveedor = proveedor;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public BigDecimal getImporteTotal() {
        return importeTotal;
    }

    public void setImporteTotal(BigDecimal importeTotal) {
        this.importeTotal = importeTotal;
    }

    public CuentaContable getCuentaGasto() {
        return cuentaGasto;
    }

    public void setCuentaGasto(CuentaContable cuentaGasto) {
        this.cuentaGasto = cuentaGasto;
    }

    public Comunidad getComunidad() {
        return comunidad;
    }

    public void setComunidad(Comunidad comunidad) {
        this.comunidad = comunidad;
    }

    public String getNumeroAsiento() {
        return numeroAsiento;
    }

    public void setNumeroAsiento(String numeroAsiento) {
        this.numeroAsiento = numeroAsiento;
    }

    public boolean isPagado() {
        return pagado;
    }

    public void setPagado(boolean pagado) {
        this.pagado = pagado;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getRutaPdf() {
        return rutaPdf;
    }

    public void setRutaPdf(String rutaPdf) {
        this.rutaPdf = rutaPdf;
    }

    // --- MÉTODOS DE UTILIDAD ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Gasto gasto = (Gasto) o;
        return Objects.equals(id, gasto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Registra el pago del gasto y establece la fecha.
     * Método de dominio para asegurar consistencia del estado 'pagado'.
     */
    public void registrarPago(LocalDate fecha) {
        this.pagado = true;
        this.fechaPago = fecha;
    }
}