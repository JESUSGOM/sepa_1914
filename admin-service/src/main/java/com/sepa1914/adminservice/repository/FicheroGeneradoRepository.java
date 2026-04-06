package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.FicheroGenerado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FicheroGeneradoRepository extends JpaRepository<FicheroGenerado, Long> {

    @Query("SELECT COUNT(f) FROM FicheroGenerado f WHERE f.comunidad.administrador.id = :usuarioId")
    long contarPorUsuario(@Param("usuarioId") Long usuarioId);

    @Query("SELECT f FROM FicheroGenerado f WHERE f.comunidad.administrador.id = :usuarioId ORDER BY f.id DESC")
    List<FicheroGenerado> findByUsuarioId(@Param("usuarioId") Long usuarioId);
}