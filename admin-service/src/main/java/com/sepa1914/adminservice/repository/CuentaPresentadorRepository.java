package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.CuentaPresentador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuentaPresentadorRepository extends JpaRepository<CuentaPresentador, Long> {

    List<CuentaPresentador> findByAdministradorId(Long administradorId);

    List<CuentaPresentador> findByAdministradorIdAndActivaTrue(Long administradorId);
}