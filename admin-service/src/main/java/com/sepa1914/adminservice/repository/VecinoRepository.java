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

    // CONTEO: Corregido para usar la ruta exacta hasta el administrador
    @Query("SELECT COUNT(v) FROM Vecino v WHERE v.comunidad.administrador.id = :usuarioId")
    long contarPorUsuario(@Param("usuarioId") Long usuarioId);

    // VERSIÓN LISTA (Para ComunidadController líneas 162, 182, 212)
    List<Vecino> findByComunidad(Comunidad comunidad);

    // VERSIÓN PAGINADA (Para las tablas de la interfaz)
    Page<Vecino> findByComunidad(Comunidad comunidad, Pageable pageable);

    List<Vecino> findByComunidadId(Long comunidadId);

    /**
     * Búsqueda segura: Solo devuelve el vecino si pertenece al administrador logueado.
     */
    @Query("SELECT v FROM Vecino v WHERE v.id = :id AND v.comunidad.administrador.username = :username")
    Optional<Vecino> findByIdAndAdminUsername(@Param("id") Long id, @Param("username") String username);
}