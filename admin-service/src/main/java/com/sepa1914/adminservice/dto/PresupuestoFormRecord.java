package com.sepa1914.adminservice.dto;

import java.util.List;

public record PresupuestoFormRecord(
        Long comunidadId,
        int anio,
        List<PresupuestoLineaRecord> lineas
) {
}