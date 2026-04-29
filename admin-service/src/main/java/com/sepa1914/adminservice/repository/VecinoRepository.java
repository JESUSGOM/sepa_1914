package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.Comunidad;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VecinoRepository extends JpaRepository<Vecino, Long> {

    /**
     * Cuenta los vecinos totales gestionados por un administrador específico.
     */
    @Query("SELECT COUNT(v) FROM Vecino v WHERE v.comunidad.administrador.id = :usuarioId")
    long contarPorUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Métodos de búsqueda estándar por entidad Comunidad.
     */
    List<Vecino> findByComunidad(Comunidad comunidad);

    Page<Vecino> findByComunidad(Comunidad comunidad, Pageable pageable);

    /**
     * Métodos de búsqueda estándar por ID de Comunidad.
     */
    List<Vecino> findByComunidadId(Long comunidadId);

    Page<Vecino> findByComunidadId(Long comunidadId, Pageable pageable);

    /**
     * Búsqueda de seguridad: valida que el vecino pertenezca al administrador actual.
     */
    @Query("SELECT v FROM Vecino v WHERE v.id = :id AND v.comunidad.administrador.username = :username")
    Optional<Vecino> findByIdAndAdminUsername(@Param("id") Long id, @Param("username") String username);

    /**
     * Búsqueda filtrada para el buscador de la interfaz (Nombre o Vivienda).
     */
    @Query("SELECT v FROM Vecino v WHERE v.comunidad.id = :comunidadId AND " +
            "(LOWER(v.nombre) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(v.vivienda) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Vecino> buscarVecinos(@Param("comunidadId") Long comunidadId, @Param("query") String query, Pageable pageable);

    /**
     * MÉTODO REQUERIDO POR BANCOSCONTROLLER:
     * Carga los vecinos y sus conceptos en una sola consulta optimizada (JOIN FETCH).
     * Esto soluciona el error [ERROR] [189,52] cannot find symbol.
     */
    @Query("SELECT DISTINCT v FROM Vecino v LEFT JOIN FETCH v.listaConceptos WHERE v.comunidad.id = :comunidadId")
    List<Vecino> findAllByComunidadIdWithConceptos(@Param("comunidadId") Long comunidadId);
}