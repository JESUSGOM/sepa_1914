package com.sepa1914.adminservice.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardStats(
        BigDecimal ingresosMes,
        BigDecimal gastosMes,
        long recibosPendientes,
        long impagadosRecientes,
        BigDecimal saldoActual,
        Map<String, BigDecimal> distribucionGastos
) {
    // Puedes añadir métodos compactos si necesitas cálculos derivados
    public BigDecimal balanceNeto() {
        return ingresosMes.subtract(gastosMes);
    }
}