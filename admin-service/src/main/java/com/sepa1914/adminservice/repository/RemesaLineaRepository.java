package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.RemesaLinea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemesaLineaRepository extends JpaRepository<RemesaLinea, Long> {

    List<RemesaLinea> findByRemesaId(Long remesaId);

    List<RemesaLinea> findByRemesaIdAndIncluidoSepaTrue(Long remesaId);
}