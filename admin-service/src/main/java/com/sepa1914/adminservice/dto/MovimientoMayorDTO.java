package com.sepa1914.adminservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para representar una línea del Libro Mayor con saldo progresivo.
 * Se utiliza un record para garantizar la inmutabilidad de los datos contables
 * durante su transporte a la vista.
 */
public record MovimientoMayorDTO(
        LocalDate fecha,
        String concepto,
        String numeroAsiento,
        BigDecimal debe,
        BigDecimal haber,
        BigDecimal saldoAcumulado
) {
    /**
     * Constructor compacto opcional para asegurar que los importes no sean nulos
     * y evitar errores de visualización en el HTML.
     */
    public MovimientoMayorDTO {
        debe = (debe != null) ? debe : BigDecimal.ZERO;
        haber = (haber != null) ? haber : BigDecimal.ZERO;
        saldoAcumulado = (saldoAcumulado != null) ? saldoAcumulado : BigDecimal.ZERO;
    }
}