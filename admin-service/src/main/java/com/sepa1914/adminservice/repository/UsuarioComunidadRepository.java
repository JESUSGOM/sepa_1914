package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.UsuarioComunidad;
import com.sepa1914.adminservice.model.UsuarioComunidadId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioComunidadRepository extends JpaRepository<UsuarioComunidad, UsuarioComunidadId> {

    List<UsuarioComunidad> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndComunidadId(Long usuarioId, Long comunidadId);
}