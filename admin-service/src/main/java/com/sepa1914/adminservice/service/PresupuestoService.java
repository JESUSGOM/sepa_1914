package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.dto.PresupuestoFormRecord;
import com.sepa1914.adminservice.dto.PresupuestoLineaRecord;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.CuentaContable;
import com.sepa1914.adminservice.model.Presupuesto;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.repository.CuentaContableRepository;
import com.sepa1914.adminservice.repository.PresupuestoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PresupuestoService {

    private final PresupuestoRepository presupuestoRepo;
    private final CuentaContableRepository cuentaRepo;
    private final ComunidadRepository comunidadRepo;

    public PresupuestoService(PresupuestoRepository presupuestoRepo,
                              CuentaContableRepository cuentaRepo,
                              ComunidadRepository comunidadRepo) {
        this.presupuestoRepo = presupuestoRepo;
        this.cuentaRepo = cuentaRepo;
        this.comunidadRepo = comunidadRepo;
    }

    /**
     * Construye el record inmutable con todas las cuentas del Grupo 6 (Gastos) y 7 (Ingresos).
     */
    public PresupuestoFormRecord obtenerFormularioPresupuesto(Long comunidadId, int anio) {

        // 1. Obtenemos las cuentas
        List<CuentaContable> cuentas = cuentaRepo.findByComunidadIdOrderByCodigoAsc(comunidadId)
                .stream()
                .filter(c -> c.getCodigo().startsWith("6") || c.getCodigo().startsWith("7"))
                .toList();

        // 2. Preparamos la lista inmutable de líneas
        List<PresupuestoLineaRecord> lineas = new ArrayList<>();

        for (CuentaContable cuenta : cuentas) {
            Optional<Presupuesto> presGuardado = presupuestoRepo.findByComunidadIdAndCuentaIdAndAnio(comunidadId, cuenta.getId(), anio);
            BigDecimal importe = presGuardado.map(Presupuesto::getImporte).orElse(BigDecimal.ZERO);

            // Creamos el record de la línea y lo añadimos a la lista temporal
            lineas.add(new PresupuestoLineaRecord(
                    cuenta.getId(),
                    cuenta.getCodigo(),
                    cuenta.getNombre(),
                    importe
            ));
        }

        // 3. Devolvemos el Record principal ya sellado con sus datos
        return new PresupuestoFormRecord(comunidadId, anio, lineas);
    }

    /**
     * Recibe el record desde el controlador y guarda los datos.
     */
    @Transactional
    public void guardarPresupuesto(PresupuestoFormRecord form) {
        Comunidad comunidad = comunidadRepo.findById(form.comunidadId())
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        for (PresupuestoLineaRecord linea : form.lineas()) {
            CuentaContable cuenta = cuentaRepo.findById(linea.cuentaId())
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));

            Presupuesto presupuesto = presupuestoRepo.findByComunidadIdAndCuentaIdAndAnio(
                            comunidad.getId(), cuenta.getId(), form.anio())
                    .orElse(new Presupuesto());

            presupuesto.setComunidad(comunidad);
            presupuesto.setCuenta(cuenta);
            presupuesto.setAnio(form.anio());
            presupuesto.setImporte(linea.importe() != null ? linea.importe() : BigDecimal.ZERO);

            presupuestoRepo.save(presupuesto);
        }
    }
}