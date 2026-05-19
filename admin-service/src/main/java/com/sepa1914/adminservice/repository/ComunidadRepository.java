package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComunidadRepository extends JpaRepository<Comunidad, Long> {

    @Query("SELECT COUNT(c) FROM Comunidad c WHERE c.administrador.id = :usuarioId")
    long contarPorUsuario(@Param("usuarioId") Long usuarioId);

    // Método para obtener todas sin paginar (útil para informes PDF globales)
    List<Comunidad> findByAdministrador(Usuario administrador);

    // --- NUEVOS MÉTODOS PARA PAGINACIÓN Y BÚSQUEDA ---

    /**
     * Obtiene las comunidades de un administrador con paginación estándar.
     */
    Page<Comunidad> findByAdministrador(Usuario administrador, Pageable pageable);

    /**
     * Realiza una búsqueda paginada filtrando por administrador y un texto
     * que coincida con nombre, dirección o población.
     */
    @Query("SELECT c FROM Comunidad c WHERE c.administrador = :admin AND (" +
            "LOWER(c.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            "LOWER(c.direccion) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            "LOWER(c.poblacion) LIKE LOWER(CONCAT('%', :texto, '%')))")
    Page<Comunidad> buscarPorAdminYTexto(@Param("admin") Usuario admin,
                                         @Param("texto") String texto,
                                         Pageable pageable);
}