package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComunidadRepository extends JpaRepository<Comunidad, Long> {
    // Devuelve solo las comunidades que pertenecen al administrador logueado
    List<Comunidad> findByAdministrador(Usuario administrador);
}