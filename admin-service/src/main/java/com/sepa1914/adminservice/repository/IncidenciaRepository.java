package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {

    /**
     * Obtiene todas las incidencias de una comunidad específica.
     */
    List<Incidencia> findByComunidadId(Long comunidadId);

    /**
     * Busca incidencias por estado dentro de una comunidad.
     * Útil para el Balance de Situación (identificar deudas pendientes de pago).
     */
    List<Incidencia> findByComunidadIdAndEstado(Long comunidadId, Incidencia.EstadoIncidencia estado);

    /**
     * Cuenta cuántas incidencias hay abiertas o en proceso.
     */
    long countByComunidadIdAndEstadoIn(Long comunidadId, List<Incidencia.EstadoIncidencia> estados);

    /**
     * NUEVO: Cuenta el total de incidencias en un estado concreto para el Dashboard Global.
     * Este método es el que soluciona el error en HomeController.java
     */
    long countByEstado(Incidencia.EstadoIncidencia estado);
}