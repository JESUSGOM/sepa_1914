package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Acta;
import com.sepa1914.adminservice.model.Comunidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActaRepository extends JpaRepository<Acta, Long> {
    // Filtro crítico para el aislamiento: Solo actas de una comunidad
    List<Acta> findByComunidad(Comunidad comunidad);
}