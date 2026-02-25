package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.Comunidad;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VecinoRepository extends JpaRepository<Vecino, Long> {
    // Para listar solo los vecinos de una comunidad concreta
    List<Vecino> findByComunidad(Comunidad comunidad);
}