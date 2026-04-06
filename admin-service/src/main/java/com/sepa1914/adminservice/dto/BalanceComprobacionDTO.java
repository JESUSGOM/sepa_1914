package com.sepa1914.adminservice.dto;

import java.math.BigDecimal;

/**
 * DTO en formato record para el Balance de Comprobación (Sumas y Saldos).
 * Representa el estado contable de una cuenta en un periodo determinado,
 * mostrando el total de movimientos (Sumas) y el saldo resultante (Saldos).
 */
public record BalanceComprobacionDTO(
        String codigo,
        String nombre,
        BigDecimal sumaDebe,
        BigDecimal sumaHaber,
        BigDecimal saldoDeudor,
        BigDecimal saldoAcreedor
) {
    /**
     * Constructor compacto para asegurar que nunca viajen valores nulos a la vista
     * y evitar errores de formato en el motor Thymeleaf.
     */
    public BalanceComprobacionDTO {
        sumaDebe = (sumaDebe != null) ? sumaDebe : BigDecimal.ZERO;
        sumaHaber = (sumaHaber != null) ? sumaHaber : BigDecimal.ZERO;
        saldoDeudor = (saldoDeudor != null) ? saldoDeudor : BigDecimal.ZERO;
        saldoAcreedor = (saldoAcreedor != null) ? saldoAcreedor : BigDecimal.ZERO;
    }

    /**
     * Método de utilidad para saber si la cuenta tiene movimiento o saldo.
     * Útil si se desea filtrar cuentas "vacías" en el informe.
     */
    public boolean tieneActividad() {
        return sumaDebe.compareTo(BigDecimal.ZERO) != 0 ||
                sumaHaber.compareTo(BigDecimal.ZERO) != 0;
    }
}