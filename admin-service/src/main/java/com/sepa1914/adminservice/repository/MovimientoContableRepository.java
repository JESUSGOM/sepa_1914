package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.MovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repositorio para la gestión de los asientos y apuntes contables.
 * Permite obtener saldos y mayores respetando la independencia por comunidad.
 */
@Repository
public interface MovimientoContableRepository extends JpaRepository<MovimientoContable, Long> {

    void deleteByComunidadId(Long comunidadId);

    /**
     * Obtiene todos los movimientos de una cuenta específica (Libro Mayor).
     */
    List<MovimientoContable> findByCuentaIdOrderByFechaAsc(Long cuentaId);

    /**
     * Obtiene todos los movimientos de una comunidad para un rango de fechas.
     */
    List<MovimientoContable> findByComunidadIdOrderByFechaDesc(Long comunidadId);

    /**
     * Calcula el saldo actual de una cuenta (Debe - Haber).
     * Útil para saber cuánto debe un vecino o cuánto hay en el banco.
     */
    @Query("SELECT COALESCE(SUM(m.debe), 0) - COALESCE(SUM(m.haber), 0) FROM MovimientoContable m WHERE m.cuenta.id = :cuentaId")
    BigDecimal getSaldoCuenta(@Param("cuentaId") Long cuentaId);

    /**
     * Obtiene todos los movimientos que componen un asiento completo.
     */
    List<MovimientoContable> findByNumeroAsiento(String numeroAsiento);

    /**
     * Busca apuntes cuyo número de asiento contenga una cadena específica.
     * ESENCIAL para la desconciliación de movimientos bancarios.
     */
    List<MovimientoContable> findByNumeroAsientoContaining(String fragmentoAsiento);

    /**
     * Suma total de gastos reales para comparar con el presupuesto.
     * Filtra por el código de cuenta (Grupo 6) y comunidad.
     */
    @Query("SELECT COALESCE(SUM(m.debe), 0) FROM MovimientoContable m WHERE m.cuenta.codigo = :codigoCuenta AND m.comunidad.id = :comunidadId")
    BigDecimal getGastoRealPorCuenta(@Param("codigoCuenta") String codigoCuenta, @Param("comunidadId") Long comunidadId);

    /**
     * Suma el debe por cuenta y ejercicio (GASTOS).
     * Requerido por ContabilidadService e informes de liquidación.
     */
    @Query("SELECT COALESCE(SUM(m.debe), 0) FROM MovimientoContable m WHERE m.cuenta.id = :cuentaId AND YEAR(m.fecha) = :anio")
    BigDecimal sumDebeByCuentaAndAno(@Param("cuentaId") Long cuentaId, @Param("anio") int anio);

    /**
     * Suma el haber por cuenta y ejercicio (INGRESOS).
     * Requerido por ContabilidadService e informes de liquidación.
     */
    @Query("SELECT COALESCE(SUM(m.haber), 0) FROM MovimientoContable m WHERE m.cuenta.id = :cuentaId AND YEAR(m.fecha) = :anio")
    BigDecimal sumHaberByCuentaAndAno(@Param("cuentaId") Long cuentaId, @Param("anio") int anio);

    /**
     * Comprueba si ya existe un asiento de devengo para un vecino en un mes concreto.
     * Evita duplicados al generar remesas SEPA.
     */
    @Query("SELECT COUNT(m) > 0 FROM MovimientoContable m " +
            "WHERE m.comunidad.id = :comunidadId " +
            "AND m.concepto LIKE %:nombreVecino% " +
            "AND MONTH(m.fecha) = :mes " +
            "AND YEAR(m.fecha) = :anio")
    boolean existsByComunidadIdAndVecinoAndPeriodo(@Param("comunidadId") Long comunidadId,
                                                   @Param("nombreVecino") String nombreVecino,
                                                   @Param("mes") int mes,
                                                   @Param("anio") int anio);

    /**
     * Elimina los asientos de devengo generados para un mes específico.
     * Se usa antes de regenerar remesas para limpiar datos previos.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MovimientoContable m WHERE m.comunidad.id = :comunidadId " +
            "AND (m.concepto LIKE '%EMISIÓN RECIBO%' OR m.concepto LIKE 'DEVENGÓ CUOTAS MES%') " +
            "AND MONTH(m.fecha) = :mes AND YEAR(m.fecha) = :anio")
    void deleteDevengosMes(@Param("comunidadId") Long comunidadId, @Param("mes") int mes, @Param("anio") int anio);

    /**
     * Obtiene los movimientos filtrados por año para el Libro Mayor.
     */
    @Query("SELECT m FROM MovimientoContable m WHERE m.cuenta.id = :cuentaId AND YEAR(m.fecha) = :anio ORDER BY m.fecha ASC, m.id ASC")
    List<MovimientoContable> findByCuentaIdAndAnioOrderByFechaAsc(@Param("cuentaId") Long cuentaId, @Param("anio") int anio);

    /**
     * Suma lo pagado realmente (tesorería) para la columna C2 de liquidación.
     */
    @Query("SELECT COALESCE(SUM(m.haber), 0) FROM MovimientoContable m WHERE m.cuenta.id = :cuentaId AND YEAR(m.fecha) = :anio AND m.numeroAsiento LIKE 'PAG-%'")
    BigDecimal sumPagadoRealByCuentaAndAno(@Param("cuentaId") Long cuentaId, @Param("anio") int anio);

    /**
     * Borra un asiento completo (vital para ediciones de facturas o anulaciones).
     */
    @Modifying
    @Transactional
    void deleteByNumeroAsiento(String numeroAsiento);

    /**
     * Recupera todos los movimientos de una comunidad para un año específico (Libro Diario).
     */
    @Query("SELECT m FROM MovimientoContable m WHERE m.comunidad.id = :comId AND YEAR(m.fecha) = :anio ORDER BY m.fecha DESC, m.id DESC")
    List<MovimientoContable> findByComunidadIdAndAnio(@Param("comId") Long comunidadId, @Param("anio") int anio);

    /**
     * Paginación para el Libro Diario.
     */
    @Query("SELECT m FROM MovimientoContable m WHERE m.comunidad.id = :comunidadId AND YEAR(m.fecha) = :anio")
    Page<MovimientoContable> findByComunidadIdAndAnio(Long comunidadId, int anio, Pageable pageable);

    /**
     * Obtiene todos los movimientos de una comunidad en un año, ordenados cronológicamente
     * para el proceso de renumeración oficial de asientos (Cierre de Ejercicio).
     */
    @Query("SELECT m FROM MovimientoContable m WHERE m.comunidad.id = :comunidadId AND YEAR(m.fecha) = :anio ORDER BY m.fecha ASC, m.id ASC")
    List<MovimientoContable> findByComunidadIdAndAnioOrderByFechaAscIdAsc(@Param("comunidadId") Long comunidadId, @Param("anio") int anio);

    List<MovimientoContable> findByNumeroAsientoLike(String patron);

    @Modifying
    @Transactional
    @Query("DELETE FROM MovimientoContable m WHERE m.comunidad.id = :comunidadId AND m.numeroAsiento NOT LIKE 'APE-%'")
    void deleteByComunidadIdExceptApertura(@Param("comunidadId") Long comunidadId);
}