package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Recibo;
import com.sepa1914.adminservice.model.Recibo.EstadoRecibo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Repositorio avanzado para la gestión de Recibos y Deuda de SEPA 1914.
 * Soporta pagos parciales, conciliación en cascada y reportes de liquidación.
 * MANTIENE TODAS LAS FUNCIONALIDADES ORIGINALES Y CORRIGE LAS CONSULTAS DE LIQUIDACIÓN.
 */
@Repository
public interface ReciboRepository extends JpaRepository<Recibo, Long> {

    /**
     * Recupera todos los recibos de una comunidad.
     * OPTIMIZACIÓN GTI: JOIN FETCH para evitar goteo de consultas a vecinos.
     */
    @Query("SELECT r FROM Recibo r JOIN FETCH r.vecino WHERE r.comunidad.id = :comunidadId")
    List<Recibo> findByComunidadId(@Param("comunidadId") Long comunidadId);

    /**
     * Recupera todos los recibos de una comunidad con paginación.
     * OPTIMIZACIÓN GTI: JOIN FETCH para evitar N+1 en el grid de listado.
     */
    @Query(value = "SELECT r FROM Recibo r JOIN FETCH r.vecino WHERE r.comunidad.id = :comId ORDER BY r.fechaEmision ASC",
            countQuery = "SELECT count(r) FROM Recibo r WHERE r.comunidad.id = :comId")
    Page<Recibo> findByComunidadIdOrderByFechaEmisionAsc(@Param("comId") Long comId, Pageable pageable);

    /**
     * Busca recibos por un estado específico.
     */
    List<Recibo> findByComunidadIdAndEstado(Long comunidadId, EstadoRecibo estado);

    /**
     * Busca todos los recibos de un vecino que no estén totalmente cobrados.
     */
    List<Recibo> findByVecinoIdAndEstadoNotOrderByFechaEmisionAsc(Long vecinoId, EstadoRecibo estado);

    /**
     * Comprueba si ya existe un recibo para una propiedad específica en un mes concreto.
     */
    @Query("SELECT COUNT(r) > 0 FROM Recibo r WHERE r.vecino.id = :vecinoId AND MONTH(r.fechaEmision) = :mes AND YEAR(r.fechaEmision) = :anio")
    boolean existsByVecinoIdAndMesAndAnio(@Param("vecinoId") Long vecinoId, @Param("mes") int mes, @Param("anio") int anio);

    /**
     * Borra los recibos de un estado específico para un mes y año.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Recibo r WHERE r.comunidad.id = :comunidadId AND MONTH(r.fechaEmision) = :mes AND YEAR(r.fechaEmision) = :anio AND r.estado = :estado")
    void deleteRecibosPorEstadoMesAnio(@Param("comunidadId") Long comunidadId, @Param("mes") int mes, @Param("anio") int anio, @Param("estado") EstadoRecibo estado);

    /**
     * Compatibilidad con el método de limpieza antes de remesa.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Recibo r WHERE r.comunidad.id = :comunidadId AND MONTH(r.fechaEmision) = :mes AND YEAR(r.fechaEmision) = :anio AND r.estado = com.sepa1914.adminservice.model.Recibo.EstadoRecibo.PENDIENTE")
    void deleteRecibosNoCobradosMes(@Param("comunidadId") Long comunidadId, @Param("mes") int mes, @Param("anio") int anio);

    /**
     * Recupera recibos filtrando por varios estados.
     * OPTIMIZACIÓN GTI: JOIN FETCH para carga masiva de propietarios.
     */
    @Query("SELECT r FROM Recibo r JOIN FETCH r.vecino WHERE r.comunidad.id = :comId AND r.estado IN :estados")
    List<Recibo> findByComunidadIdAndEstadoIn(@Param("comId") Long comunidadId, @Param("estados") Collection<EstadoRecibo> estados);

    /**
     * Localiza recibos por importe exacto y estado (CONCILIACIÓN AUTOMÁTICA).
     */
    List<Recibo> findByComunidadIdAndImporteAndEstado(Long comunidadId, BigDecimal importe, EstadoRecibo estado);

    /**
     * Conteo rápido para widgets del Dashboard.
     */
    long countByComunidadIdAndEstado(Long comunidadId, EstadoRecibo estado);

    /**
     * Recupera una lista de recibos filtrada por varios estados.
     */
    @Query("SELECT r FROM Recibo r WHERE r.comunidad.id = :comunidadId AND r.estado IN :estados")
    List<Recibo> findByComunidadIdAndEstadosList(@Param("comunidadId") Long comunidadId, @Param("estados") List<EstadoRecibo> estados);

    /**
     * Suma total de deuda por comunidad filtrando por estados.
     */
    @Query("SELECT SUM(r.importe) FROM Recibo r WHERE r.comunidad.id = :comunidadId AND r.estado IN :estados")
    BigDecimal sumDeudaTotalByComunidadAndEstados(@Param("comunidadId") Long comunidadId, @Param("estados") List<EstadoRecibo> estados);

    /**
     * MÉTODO PARA LIQUIDACIÓN: Suma importes por comunidad, mes, año y estados.
     */
    @Query("SELECT SUM(r.importe) FROM Recibo r " +
            "WHERE r.comunidad.id = :comunidadId " +
            "AND MONTH(r.fechaEmision) = :mes " +
            "AND YEAR(r.fechaEmision) = :anio " +
            "AND r.estado IN :estados")
    BigDecimal sumImporteByComunidadAndMesAndEstados(
            @Param("comunidadId") Long comunidadId,
            @Param("mes") int mes,
            @Param("anio") int anio,
            @Param("estados") List<EstadoRecibo> estados);

    /**
     * Suma lo realmente cobrado (pagadoAcumulado) en un periodo.
     */
    @Query("SELECT SUM(r.pagadoAcumulado) FROM Recibo r " +
            "WHERE r.comunidad.id = :comunidadId " +
            "AND MONTH(r.fechaEmision) = :mes " +
            "AND YEAR(r.fechaEmision) = :anio")
    BigDecimal sumPagadoRealByComunidadAndMes(
            @Param("comunidadId") Long comunidadId,
            @Param("mes") int mes,
            @Param("anio") int anio);

    /**
     * NUEVO: Suma importe EMITIDO TOTAL por comunidad y año (REQUERIDO POR SERVICE).
     */
    @Query("SELECT SUM(r.importe) FROM Recibo r WHERE r.comunidad.id = :comId AND YEAR(r.fechaEmision) = :anio")
    BigDecimal sumImporteByComunidadAndAnio(@Param("comId") Long comId, @Param("anio") int anio);

    /**
     * NUEVO: Suma importe COBRADO TOTAL por comunidad y año (REQUERIDO POR SERVICE).
     */
    @Query("SELECT SUM(r.pagadoAcumulado) FROM Recibo r WHERE r.comunidad.id = :comId AND YEAR(r.fechaEmision) = :anio AND r.estado = com.sepa1914.adminservice.model.Recibo.EstadoRecibo.COBRADO")
    BigDecimal sumCobradoByComunidadAndAnio(@Param("comId") Long comId, @Param("anio") int anio);

    /**
     * Lista recibos pendientes ordenados para ver las fechas.
     * OPTIMIZACIÓN GTI: JOIN FETCH para agilizar la pantalla de liquidación.
     */
    @Query("SELECT r FROM Recibo r JOIN FETCH r.vecino WHERE r.comunidad.id = :comId AND r.estado = :estado ORDER BY r.fechaEmision DESC")
    List<Recibo> findByComunidadIdAndEstadoOrderByFechaEmisionAsc(Long comunidadId, EstadoRecibo estado);

    /**
     * Sumatorio Anual corregido (Soporta Mes nulo para totales anuales).
     */
    @Query("SELECT SUM(r.importe) FROM Recibo r WHERE r.comunidad.id = :comId " +
            "AND (:mes IS NULL OR MONTH(r.fechaEmision) = :mes) " +
            "AND YEAR(r.fechaEmision) = :anio")
    BigDecimal sumImporteAnual(@Param("comId") Long comId, @Param("anio") int anio, @Param("mes") Integer mes);

    /**
     * Obtiene los conceptos únicos facturados en una comunidad.
     */
    @Query("SELECT DISTINCT r.concepto FROM Recibo r WHERE r.comunidad.id = :comId")
    List<String> findDistinctConceptosByComunidad(@Param("comId") Long comId);

    /**
     * Suma importe EMITIDO por concepto y año (Columna B3).
     */
    @Query("SELECT SUM(r.importe) FROM Recibo r WHERE r.comunidad.id = :comunidadId AND r.concepto = :concepto AND YEAR(r.fechaEmision) = :anio")
    BigDecimal sumImporteByConceptoAndAnio(@Param("comunidadId") Long comunidadId, @Param("concepto") String concepto, @Param("anio") int anio);

    /**
     * Suma importe COBRADO REAL por concepto y año (Columna B2).
     */
    @Query("SELECT SUM(r.importe) FROM Recibo r WHERE r.comunidad.id = :comId AND r.concepto = :concepto AND YEAR(r.fechaEmision) = :anio AND r.estado = com.sepa1914.adminservice.model.Recibo.EstadoRecibo.COBRADO")
    BigDecimal sumCobradoByConceptoAndAnio(@Param("comId") Long comId, @Param("concepto") String concepto, @Param("anio") int anio);

    @Query("SELECT SUM(r.pagadoAcumulado) FROM Recibo r WHERE r.comunidad.id = :comunidadId AND YEAR(r.fechaEmision) = :anio")
    BigDecimal sumPagadoByComunidadAndAnio(@Param("comunidadId") Long comunidadId, @Param("anio") int anio);

    // Dentro de ReciboRepository.java
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Recibo r WHERE r.vecino.id = :vecinoId AND r.concepto = :concepto AND MONTH(r.fechaEmision) = :mes AND YEAR(r.fechaEmision) = :anio")
    boolean existsByVecinoIdAndConceptoAndMesAndAnio(@Param("vecinoId") Long vecinoId, @Param("concepto") String concepto, @Param("mes") int mes, @Param("anio") int anio);

    @Query("SELECT SUM(r.pagadoAcumulado) FROM Recibo r WHERE r.comunidad.id = :comunidadId AND r.concepto = :concepto AND YEAR(r.fechaEmision) = :anio")
    BigDecimal sumPagadoByComunidadAndConceptoAndAnio(@Param("comunidadId") Long comunidadId, @Param("concepto") String concepto, @Param("anio") int anio);

    @Query("SELECT SUM(r.pagadoAcumulado) FROM Recibo r WHERE r.comunidad.id = :comunidadId AND r.concepto IN :conceptos AND YEAR(r.fechaEmision) = :anio")
    BigDecimal sumPagadoByComunidadAndConceptosList(@Param("comunidadId") Long comunidadId, @Param("conceptos") List<String> conceptos, @Param("anio") int anio);

    List<Recibo> findByMovimientoBancarioId(Long movId);

    List<Recibo> findByVecinoId(Long vecinoId);
    List<Recibo> findByVecinoIdAndEstadoNot(Long vecinoId, EstadoRecibo estado);

    List<Recibo> findByVecinoIdOrderByFechaEmisionAsc(Long vecinoId);
}