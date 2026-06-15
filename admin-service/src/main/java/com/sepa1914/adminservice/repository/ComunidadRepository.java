package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComunidadRepository extends JpaRepository<Comunidad, Long> {

    @Query("""
           SELECT COUNT(DISTINCT c)
           FROM Comunidad c
           LEFT JOIN UsuarioComunidad uc ON uc.comunidad = c
           WHERE c.administrador.id = :usuarioId
              OR uc.usuario.id = :usuarioId
           """)
    long contarPorUsuario(@Param("usuarioId") Long usuarioId);

    List<Comunidad> findByAdministrador(Usuario administrador);

    Page<Comunidad> findByAdministrador(Usuario administrador, Pageable pageable);

    @Query("""
           SELECT DISTINCT c
           FROM Comunidad c
           LEFT JOIN UsuarioComunidad uc ON uc.comunidad = c
           WHERE (c.administrador = :admin OR uc.usuario = :admin)
           """)
    List<Comunidad> findAccesiblesPorUsuario(@Param("admin") Usuario admin);

    @Query("""
           SELECT DISTINCT c
           FROM Comunidad c
           LEFT JOIN UsuarioComunidad uc ON uc.comunidad = c
           WHERE (c.administrador = :admin OR uc.usuario = :admin)
           """)
    Page<Comunidad> findAccesiblesPorUsuario(@Param("admin") Usuario admin, Pageable pageable);

    @Query("""
           SELECT DISTINCT c
           FROM Comunidad c
           LEFT JOIN UsuarioComunidad uc ON uc.comunidad = c
           WHERE (c.administrador = :admin OR uc.usuario = :admin)
             AND (
                LOWER(c.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) OR
                LOWER(c.direccion) LIKE LOWER(CONCAT('%', :texto, '%')) OR
                LOWER(c.poblacion) LIKE LOWER(CONCAT('%', :texto, '%'))
             )
           """)
    Page<Comunidad> buscarAccesiblesPorUsuarioYTexto(@Param("admin") Usuario admin,
                                                     @Param("texto") String texto,
                                                     Pageable pageable);

    @Query("""
           SELECT c
           FROM Comunidad c
           WHERE c.administrador = :admin
             AND (
                LOWER(c.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) OR
                LOWER(c.direccion) LIKE LOWER(CONCAT('%', :texto, '%')) OR
                LOWER(c.poblacion) LIKE LOWER(CONCAT('%', :texto, '%'))
             )
           """)
    Page<Comunidad> buscarPorAdminYTexto(@Param("admin") Usuario admin,
                                         @Param("texto") String texto,
                                         Pageable pageable);
}