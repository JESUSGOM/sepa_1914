package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GastoRepository extends JpaRepository<Gasto, Long> {
    /**
     * Recupera todos los gastos de una comunidad ordenados por fecha de factura.
     * Útil para el listado principal de facturas y gastos.
     */
    List<Gasto> findByComunidadIdOrderByFechaDesc(Long comunidadId);

    /**
     * Recupera la lista de gastos de una comunidad.
     * CORREGIDO: Se cambia el tipo de retorno de Object a List<Gasto>
     * para eliminar errores de tipado en el GastoController.
     */
    List<Gasto> findByComunidadId(Long comunidadId);

    /**
     * Busca una factura por su número de asiento contable.
     * Vital para la lógica de "registrarGastoContable" y evitar duplicados.
     */
    Optional<Gasto> findByNumeroAsiento(String numeroAsiento);

    /**
     * Verifica si ya existe una factura con un número determinado en una comunidad.
     */
    boolean existsByNumeroFacturaAndComunidadId(String numeroFactura, Long comunidadId);

    List<Gasto> findByComunidadIdAndPagadoFalse(Long comunidadId);

    @Query("SELECT SUM(g.importeTotal) FROM Gasto g WHERE g.cuentaGasto.id = :cuentaId AND g.pagado = true AND YEAR(g.fechaPago) = :anio")
    BigDecimal sumImportePagadoByCuentaAndAnio(@Param("cuentaId") Long cuentaId, @Param("anio") int anio);

    @Query("SELECT g.cuentaGasto.id, SUM(g.importeTotal) FROM Gasto g " +
            "WHERE g.comunidad.id = :comId AND g.pagado = true AND YEAR(g.fechaPago) = :anio " +
            "GROUP BY g.cuentaGasto.id")
    List<Object[]> sumAllPagadoByComunidadAndAnio(@Param("comId") Long comId, @Param("anio") int anio);
}