package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.ConfiguracionRutas;
import com.sepa1914.adminservice.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para la gestión de la configuración de rutas locales.
 * Refactorizado para permitir la búsqueda personalizada por Administrador (Usuario).
 */
@Repository
public interface ConfiguracionRutasRepository extends JpaRepository<ConfiguracionRutas, Long> {

    /**
     * Busca la configuración de rutas asociada a un administrador específico.
     * Fundamental para la Opción B (Multi-usuario).
     * * @param administrador El usuario logueado actualmente.
     * @return Un Optional con la configuración si existe.
     */
    Optional<ConfiguracionRutas> findByAdministrador(Usuario administrador);

}