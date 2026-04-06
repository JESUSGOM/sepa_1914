package com.sepa1914.adminservice.dto;

public record IncidenciaReport(
        Long id,
        String titulo,
        String estado,
        String prioridad,
        long diasAbierta
) {}