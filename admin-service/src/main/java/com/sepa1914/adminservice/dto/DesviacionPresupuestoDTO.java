package com.sepa1914.adminservice.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO como Record para compatibilidad total con Java 21.
 * Refactorizado para soportar contabilidad de devengo (Facturado) vs Caja (Real).
 * INTEGRACIÓN: Añadido campo de auditoría para detectar descuadres con la gestión de recibos.
 */
public record DesviacionPresupuestoDTO(
        Long idCuenta,
        String codigoCuenta,
        String nombreCuenta,
        BigDecimal importePresupuestado, // B1 / C1
        BigDecimal importeReal,          // B2 / C2 (Cobrado/Pagado efectivamente)
        BigDecimal importeFacturado,     // B3 / C3 (Saldos en el Libro Mayor)
        BigDecimal diferencia,           // B4 / C4 (Diferencia entre Facturado y Real)
        BigDecimal importeRecibosGestion // NUEVO: Suma real de la tabla recibos para auditoría
) {
    /**
     * CONSTRUCTOR COMPACTO
     * Valida nulos y asigna valores por defecto. Fundamental para evitar NullPointerException en la UI.
     */
    public DesviacionPresupuestoDTO {
        importePresupuestado = (importePresupuestado == null) ? BigDecimal.ZERO : importePresupuestado;
        importeReal = (importeReal == null) ? BigDecimal.ZERO : importeReal;
        importeFacturado = (importeFacturado == null) ? BigDecimal.ZERO : importeFacturado;
        importeRecibosGestion = (importeRecibosGestion == null) ? importeFacturado : importeRecibosGestion;
        diferencia = (diferencia == null) ? importeFacturado.subtract(importeReal) : diferencia;
        codigoCuenta = (codigoCuenta == null) ? "700" : codigoCuenta;
        nombreCuenta = (nombreCuenta == null) ? "" : nombreCuenta;
    }

    /**
     * CONSTRUCTOR SECUNDARIO (Mantenido para compatibilidad con Service original)
     */
    public DesviacionPresupuestoDTO(Long idCuenta, String codigoCuenta, String nombreCuenta,
                                    BigDecimal importePresupuestado, BigDecimal importeReal, BigDecimal desviacion) {
        this(idCuenta, codigoCuenta, nombreCuenta, importePresupuestado, importeReal, importeReal, desviacion, importeReal);
    }

    /**
     * CONSTRUCTOR PARA COMPATIBILIDAD CON FACTURADO (Original - 69 líneas)
     */
    public DesviacionPresupuestoDTO(Long idCuenta, String codigoCuenta, String nombreCuenta,
                                    BigDecimal importePresupuestado, BigDecimal importeReal,
                                    BigDecimal importeFacturado, BigDecimal diferencia) {
        this(idCuenta, codigoCuenta, nombreCuenta, importePresupuestado, importeReal, importeFacturado, diferencia, importeFacturado);
    }

    /**
     * CONSTRUCTOR PARA RESÚMENES MENSUALES (Original)
     */
    public DesviacionPresupuestoDTO(String nombreCuenta, BigDecimal importeReal) {
        this(null, "700", nombreCuenta, BigDecimal.ZERO, importeReal, importeReal, BigDecimal.ZERO, importeReal);
    }

    // --- MÉTODOS DE CÁLCULO ---

    public boolean isDescuadrado() {
        return importeFacturado.compareTo(importeRecibosGestion) != 0;
    }

    public BigDecimal getDescuadreAbsoluto() {
        return importeFacturado.subtract(importeRecibosGestion).abs();
    }

    public double getPorcentajeCumplimientoFacturado() {
        if (importePresupuestado.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return importeFacturado.multiply(new BigDecimal("100"))
                .divide(importePresupuestado, 2, RoundingMode.HALF_UP).doubleValue();
    }

    public double getPorcentajeCumplimientoReal() {
        if (importePresupuestado.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return importeReal.multiply(new BigDecimal("100"))
                .divide(importePresupuestado, 2, RoundingMode.HALF_UP).doubleValue();
    }
}