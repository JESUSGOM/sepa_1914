package com.sepa1914.adminservice.dto;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa el Balance de Situación consolidado de una comunidad.
 * Refactorizado para garantizar nulidad cero y precisión decimal.
 */
public record BalanceSituacion(
        // ACTIVO
        BigDecimal saldoBancos,
        BigDecimal deudasVecinos,
        BigDecimal otrosActivos,

        // PASIVO
        BigDecimal deudasProveedores,
        BigDecimal fondosReserva,
        BigDecimal resultadoEjercicio,

        // TOTALES
        BigDecimal totalActivo,
        BigDecimal totalPasivo
) {
    /**
     * Constructor compacto para asegurar que ningún campo sea nulo.
     * Si un valor llega nulo desde el Service, se convierte automáticamente en ZERO.
     */
    public BalanceSituacion {
        saldoBancos = Objects.requireNonNullElse(saldoBancos, BigDecimal.ZERO);
        deudasVecinos = Objects.requireNonNullElse(deudasVecinos, BigDecimal.ZERO);
        otrosActivos = Objects.requireNonNullElse(otrosActivos, BigDecimal.ZERO);
        deudasProveedores = Objects.requireNonNullElse(deudasProveedores, BigDecimal.ZERO);
        fondosReserva = Objects.requireNonNullElse(fondosReserva, BigDecimal.ZERO);
        resultadoEjercicio = Objects.requireNonNullElse(resultadoEjercicio, BigDecimal.ZERO);
        totalActivo = Objects.requireNonNullElse(totalActivo, BigDecimal.ZERO);
        totalPasivo = Objects.requireNonNullElse(totalPasivo, BigDecimal.ZERO);
    }

    /**
     * Verifica si el balance está cuadrado.
     * @return true si Activo == Pasivo
     */
    public boolean estaCuadrado() {
        return totalActivo.compareTo(totalPasivo) == 0;
    }
}