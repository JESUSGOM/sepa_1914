package com.sepa1914.adminservice.model;

/**
 * Esto es un ENUM, no un record.
 * Define los estados oficiales de la base de datos.
 */
public enum EstadoRecibo {
    PENDIENTE,
    COBRADO,
    DEVUELTO,
    ANULADO
}