package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador para la gestión de conceptos de cobro.
 * REPARADO: Filtrado de sugeridos a cero y gestión de mes de inicio.
 * MANTIENE TODA LA FUNCIONALIDAD ORIGINAL.
 */
@Controller
@RequestMapping("/conceptos")
public class ConceptoController {

    private static final Logger log = LoggerFactory.getLogger(ConceptoController.class);

    @Autowired private ConceptoCobroRepository conceptoRepository;
    @Autowired private VecinoRepository vecinoRepository;
    @Autowired private CuentaContableRepository cuentaRepository;
    @Autowired private ComunidadRepository comunidadRepository;
    @Autowired private PresupuestoRepository presupuestoRepo;

    /**
     * Muestra el listado de conceptos maestros (plantillas globales).
     */
    @GetMapping("/maestro")
    public String listarMaestro(@RequestParam(required = false) Long comunidadId, Model model) {
        Long idParaCuentas = (comunidadId != null) ? comunidadId : 3L;

        List<ConceptoCobro> maestros = conceptoRepository.findByVecinoIsNull();
        List<CuentaContable> ingresos = cuentaRepository.findByComunidadIdAndTipo(idParaCuentas, TipoCuenta.INGRESO);
        List<CuentaContable> pasivos = cuentaRepository.findByComunidadIdAndTipo(idParaCuentas, TipoCuenta.PASIVO);

        model.addAttribute("cuentasIngreso", ingresos);
        model.addAttribute("cuentasPasivo", pasivos);
        model.addAttribute("conceptosMaestros", maestros);
        model.addAttribute("activePage", "maestro-conceptos");

        ConceptoCobro nuevoMaestro = new ConceptoCobro();
        nuevoMaestro.setMesInicio(LocalDate.now().getMonthValue()); // Sugerir mes actual
        nuevoMaestro.setActivo(true);
        model.addAttribute("nuevoMaestro", nuevoMaestro);

        return "conceptos-maestro";
    }

    @PostMapping("/maestro/guardar")
    public String guardarMaestro(@ModelAttribute ConceptoCobro concepto,
                                 @RequestParam(required = false) Long cuentaContableId) {

        if (concepto.getImporte() == null) concepto.setImporte(BigDecimal.ZERO);
        if (concepto.getMesInicio() == null) concepto.setMesInicio(1);

        if (cuentaContableId != null) {
            cuentaRepository.findById(cuentaContableId).ifPresent(concepto::setCuentaContable);
        }

        concepto.setVecino(null);
        concepto.setComunidad(null);

        log.info("Guardando plantilla maestra: {} con inicio en mes {}", concepto.getDescripcion(), concepto.getMesInicio());
        conceptoRepository.save(concepto);
        return "redirect:/conceptos/maestro";
    }

    @PostMapping("/maestro/eliminar/{id}")
    public String eliminarMaestro(@PathVariable Long id) {
        conceptoRepository.deleteById(id);
        return "redirect:/conceptos/maestro";
    }

    /**
     * Gestiona los conceptos de un vecino.
     * REPARACIÓN: Filtra el combo para no mostrar sugeridos de 0.00€.
     */
    @GetMapping("/vecino/{vecinoId}")
    public String gestionarConceptosVecino(@PathVariable Long vecinoId, Model model) {
        Vecino vecino = vecinoRepository.findById(vecinoId).orElseThrow();
        Comunidad comunidad = vecino.getComunidad();
        int anioActual = LocalDate.now().getYear();

        List<ConceptoCobro> conceptosDelVecino = conceptoRepository.findByVecino(vecino);
        List<ConceptoCobro> todosLosMaestros = conceptoRepository.findByVecinoIsNull();

        // FILTRADO DINÁMICO: Solo maestros con presupuesto asignado (Sugerido > 0)
        List<ConceptoCobro> maestrosFiltrados = todosLosMaestros.stream().filter(m -> {
            if (m.getCuentaContable() == null) return true; // Si no tiene cuenta, se muestra para asignación manual

            BigDecimal presupuestoAnual = presupuestoRepo
                    .findByComunidadIdAndCuentaIdAndAnio(comunidad.getId(), m.getCuentaContable().getId(), anioActual)
                    .map(Presupuesto::getImporte).orElse(BigDecimal.ZERO);

            return presupuestoAnual.compareTo(BigDecimal.ZERO) > 0;
        }).collect(Collectors.toList());

        model.addAttribute("cuentasIngreso", cuentaRepository.findByComunidadIdAndTipo(comunidad.getId(), TipoCuenta.INGRESO));
        model.addAttribute("cuentasPasivo", cuentaRepository.findByComunidadIdAndTipo(comunidad.getId(), TipoCuenta.PASIVO));

        model.addAttribute("activePage", "vecinos");
        model.addAttribute("vecino", vecino);
        model.addAttribute("conceptos", conceptosDelVecino);
        model.addAttribute("conceptosMaestros", maestrosFiltrados); // Enviamos solo la lista limpia

        ConceptoCobro nuevo = new ConceptoCobro();
        nuevo.setMesInicio(LocalDate.now().getMonthValue());
        model.addAttribute("nuevoConcepto", nuevo);

        return "conceptos-vecino";
    }

    @PostMapping("/guardar")
    public String guardarConcepto(@ModelAttribute ConceptoCobro concepto,
                                  @RequestParam Long vecinoId,
                                  @RequestParam(required = false) Long cuentaContableId,
                                  RedirectAttributes ra) {
        try {
            Vecino v = vecinoRepository.findById(vecinoId).orElseThrow();
            concepto.setVecino(v);
            concepto.setComunidad(v.getComunidad());

            // REQUISITO: "CUOTA COMUNIDAD" + FINCA si es un concepto de presupuesto
            if (concepto.getDescripcion() != null && concepto.getDescripcion().contains("Presupuesto")) {
                concepto.setDescripcion("CUOTA COMUNIDAD " + (v.getVivienda() != null ? v.getVivienda() : ""));
            }

            if (concepto.getMesInicio() == null) {
                concepto.setMesInicio(LocalDate.now().getMonthValue());
            }

            if (cuentaContableId != null) {
                cuentaRepository.findById(cuentaContableId).ifPresent(concepto::setCuentaContable);
            }

            log.info("Asignando concepto a {}: {} (Inicio: mes {})", v.getNombre(), concepto.getDescripcion(), concepto.getMesInicio());
            conceptoRepository.save(concepto);
            ra.addFlashAttribute("exito", "Concepto asignado correctamente.");

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Fallo al guardar: " + e.getMessage());
        }
        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarConcepto(@PathVariable Long id, @RequestParam Long vecinoId) {
        conceptoRepository.deleteById(id);
        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    @PostMapping("/toggle-estado/{id}")
    public String toggleEstadoConcepto(@PathVariable Long id, @RequestParam Long vecinoId) {
        ConceptoCobro c = conceptoRepository.findById(id).orElseThrow();
        c.setActivo(!c.isActivo());
        conceptoRepository.save(c);
        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    @PostMapping("/editar-importe/{id}")
    public String editarImporte(@PathVariable Long id, @RequestParam BigDecimal nuevoImporte, @RequestParam Long vecinoId) {
        ConceptoCobro c = conceptoRepository.findById(id).orElseThrow();
        c.setImporte(nuevoImporte);
        conceptoRepository.save(c);
        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    @PostMapping("/maestro/actualizar")
    public String actualizarMaestro(@RequestParam Long id,
                                    @RequestParam String descripcion,
                                    @RequestParam(required = false) Long cuentaContableId,
                                    @RequestParam ConceptoCobro.Periodicidad periodicidad,
                                    @RequestParam(required = false, defaultValue = "1") Integer mesInicio) {

        ConceptoCobro c = conceptoRepository.findById(id).orElseThrow();
        c.setDescripcion(descripcion);
        c.setPeriodicidad(periodicidad);
        c.setMesInicio(mesInicio);

        if (cuentaContableId != null) {
            cuentaRepository.findById(cuentaContableId).ifPresent(c::setCuentaContable);
        }

        conceptoRepository.save(c);
        return "redirect:/conceptos/maestro";
    }
}