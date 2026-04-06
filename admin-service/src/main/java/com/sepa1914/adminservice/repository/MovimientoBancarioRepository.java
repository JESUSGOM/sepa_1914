package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.MovimientoBancario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio para la gestión de movimientos importados mediante Norma 43.
 * Refactorizado para evitar duplicidades y asegurar compatibilidad con Java 21.
 */
@Repository
public interface MovimientoBancarioRepository extends JpaRepository<MovimientoBancario, Long> {

    /**
     * Recupera todos los movimientos de una comunidad.
     * Esencial para el cálculo de saldos y métricas del Dashboard.
     */
    List<MovimientoBancario> findByComunidadId(Long comunidadId);

    /**
     * Obtiene el histórico de movimientos de una comunidad ordenado por fecha descendente.
     * Utilizado para mostrar el extracto en la vista 'bancos-lista'.
     */
    List<MovimientoBancario> findByComunidadIdOrderByFechaOperacionAsc(Long comunidadId);

    /**
     * Busca movimientos que aún no han sido conciliados.
     * Vital para los procesos de conciliación manual e inteligente.
     */
    List<MovimientoBancario> findByComunidadIdAndConciliadoFalse(Long comunidadId);

    /**
     * Método crítico para evitar duplicidad de apuntes al importar varios ficheros.
     * Verifica si ya existe un registro con la misma fecha, importe y referencia bancaria.
     */
    boolean existsByFechaOperacionAndImporteAndDocumentoExtra(
            LocalDate fechaOperacion,
            BigDecimal importe,
            String documentoExtra
    );

    /**
     * NUEVO MÉTODO: Añadido para compatibilidad con la validación por concepto del Controller.
     * NO ELIMINA NINGUNA FUNCIONALIDAD ANTERIOR.
     */
    boolean existsByFechaOperacionAndImporteAndConcepto(
            LocalDate fechaOperacion,
            BigDecimal importe,
            String concepto
    );

    /**
     * Busca movimientos por un importe exacto que no estén conciliados.
     * Útil para localizar el ingreso de una remesa completa o pagos específicos de vecinos.
     */
    List<MovimientoBancario> findByImporteAndConciliadoFalse(BigDecimal importe);

    /**
     * Busca movimientos por un rango de fechas para informes periódicos.
     */
    List<MovimientoBancario> findByComunidadIdAndFechaOperacionBetween(
            Long comunidadId,
            LocalDate fechaInicio,
            LocalDate fechaFin
    );
}