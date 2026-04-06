package com.sepa1914.adminservice.dto;

import java.math.BigDecimal;

public record PresupuestoLineaRecord(
        Long cuentaId,
        String codigoCuenta,
        String nombreCuenta,
        BigDecimal importe
) {
    // Le ponemos un pequeño bloque de inicialización para asegurarnos de que el importe nunca sea null
    public PresupuestoLineaRecord {
        if (importe == null) {
            importe = BigDecimal.ZERO;
        }
    }
}