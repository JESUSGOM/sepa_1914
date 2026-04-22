package com.sepa1914.adminservice.dto;

/**
 * DTO para el transporte de datos extraídos por el escáner de facturas.
 * Implementado como Record de Java 21 para inmutabilidad y concisión.
 */
public record GastoDTO(
        String proveedor,
        String importe,
        String cups,
        String numeroFactura,
        String fecha
) {
    // Constructor compacto para valores por defecto opcionales
    public GastoDTO {
        if (proveedor == null) proveedor = "Desconocido";
    }

    // Método de conveniencia para crear un DTO vacío o con fallos
    public static GastoDTO vacio() {
        return new GastoDTO(null, "0.00", null, null, null);
    }
}