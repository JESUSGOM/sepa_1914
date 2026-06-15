package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.CuentaPresentador;
import com.sepa1914.adminservice.repository.CuentaPresentadorRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CuentaPresentadorService {

    private final CuentaPresentadorRepository repository;

    public CuentaPresentadorService(CuentaPresentadorRepository repository) {
        this.repository = repository;
    }

    public List<CuentaPresentador> obtenerActivasAdministrador(Long administradorId) {
        return repository.findByAdministradorIdAndActivaTrue(administradorId);
    }

    public CuentaPresentador obtenerPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuenta presentador no encontrada"));
    }

    public CuentaPresentador guardar(CuentaPresentador cuenta) {
        return repository.save(cuenta);
    }

    public void eliminar(Long id) {
        repository.deleteById(id);
    }
}