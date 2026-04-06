package com.sepa1914.adminservice.model;

public enum TipoCuenta {
    ACTIVO,   // Bancos, Caja
    PASIVO,   // Deudas con proveedores, Fondo Reserva
    INGRESO,  // Cuotas (Grupo 7)
    GASTO,    // Luz, Agua, Reparaciones (Grupo 6)
    VECINO    // Subcuentas 430
}