package com.sepa1914.adminservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IncidenciaDTO(
        @JsonProperty("comunidad_id")
        Long comunidadId,

        @JsonProperty("nombre_comunidad")
        String nombreComunidad,

        String titulo,
        String descripcion,
        String prioridad,
        String fecha
) {}