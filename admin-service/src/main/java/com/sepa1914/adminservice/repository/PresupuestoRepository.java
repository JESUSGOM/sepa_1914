package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Presupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

    // Obtener todo el presupuesto de una comunidad para un año concreto
    List<Presupuesto> findByComunidadIdAndAnio(Long comunidadId, int anio);

    // Buscar si ya existe una línea de presupuesto para una cuenta específica en ese año
    Optional<Presupuesto> findByComunidadIdAndCuentaIdAndAnio(Long comunidadId, Long cuentaId, int anio);
}