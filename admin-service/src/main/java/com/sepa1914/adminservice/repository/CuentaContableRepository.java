package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.CuentaContable;
import com.sepa1914.adminservice.model.TipoCuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la gestión de Cuentas Contables.
 * Sincronizado con el campo 'codigo' tras refactorización normativa.
 * Soporta búsquedas globales por tipo para el Maestro de Conceptos.
 */
@Repository
public interface CuentaContableRepository extends JpaRepository<CuentaContable, Long> {

    // Método para informes y lógica interna (devuelve todas las cuentas)
    List<CuentaContable> findByComunidadId(Long comunidadId);

    /**
     * Recupera todas las cuentas de una comunidad específica.
     */
    Page<CuentaContable> findByComunidadId(Long comunidadId, Pageable pageable);

    /**
     * Busca una cuenta por su código y comunidad (CORREGIDO).
     */
    Optional<CuentaContable> findByCodigoAndComunidadId(String codigo, Long comunidadId);

    /**
     * Consulta JPQL corregida para obtener el código máximo de vecino.
     * Mantiene la funcionalidad de autogeneración de cuentas serie 430.
     */
    @Query("SELECT MAX(c.codigo) FROM CuentaContable c WHERE c.comunidad.id = :comId AND c.codigo LIKE '430%'")
    String findMaxCodigoVecino(@Param("comId") Long comunidadId);

    /**
     * Busca cuentas por tipo y comunidad.
     */
    List<CuentaContable> findByTipoAndComunidadId(TipoCuenta tipo, Long comunidadId);

    /**
     * Verifica si existe una cuenta con un código específico en una comunidad.
     */
    boolean existsByCodigoAndComunidadId(String codigo, Long comunidadId);

    /**
     * Busca cuentas por comunidad y tipo.
     */
    List<CuentaContable> findByComunidadIdAndTipo(Long comunidadId, TipoCuenta tipo);

    // --- MÉTODOS PARA MAESTRO DE CONCEPTOS Y NORMALIZACIÓN ---

    /**
     * Recupera cuentas de un tipo específico de forma global.
     * Esencial para plantillas donde no hay comunidadId definida.
     */
    List<CuentaContable> findByTipo(TipoCuenta tipo);

    /**
     * Verifica si existe una cuenta por tipo de forma global.
     */
    boolean existsByTipo(TipoCuenta tipo);

    List<CuentaContable> findByComunidadIdOrderByCodigoAsc(Long comunidadId);
}