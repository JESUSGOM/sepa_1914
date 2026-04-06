package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.LicenciaMaestra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LicenciaMaestraRepository extends JpaRepository<LicenciaMaestra, Long> {
    Optional<LicenciaMaestra> findByHardwareIdAndActivoTrue(String hardwareId);
}