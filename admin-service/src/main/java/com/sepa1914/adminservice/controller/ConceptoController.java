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
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador para la gestión de conceptos de cobro.
 * REFACTURADO: Conceptos Maestros Globales y eliminación de filtros restrictivos.
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
     * Muestra el listado de conceptos maestros.
     * Ahora son tratados como plantillas globales para todo el sistema.
     */
    @GetMapping("/maestro")
    public String listarMaestro(@RequestParam(required = false) Long comunidadId, Model model) {
        // Si no viene comunidad, usamos una por defecto para cargar el catálogo de cuentas sugerido
        Long idParaCuentas = (comunidadId != null) ? comunidadId : 1L;

        List<ConceptoCobro> maestros = conceptoRepository.findByVecinoIsNull();
        List<CuentaContable> ingresos = cuentaRepository.findByComunidadIdAndTipo(idParaCuentas, TipoCuenta.INGRESO);
        List<CuentaContable> pasivos = cuentaRepository.findByComunidadIdAndTipo(idParaCuentas, TipoCuenta.PASIVO);

        model.addAttribute("cuentasIngreso", ingresos);
        model.addAttribute("cuentasPasivo", pasivos);
        model.addAttribute("conceptosMaestros", maestros);
        model.addAttribute("activePage", "maestro-conceptos");

        ConceptoCobro nuevoMaestro = new ConceptoCobro();
        nuevoMaestro.setMesInicio(LocalDate.now().getMonthValue());
        nuevoMaestro.setActivo(true);
        model.addAttribute("nuevoMaestro", nuevoMaestro);

        return "conceptos-maestro";
    }

    /**
     * Guarda una plantilla maestra. Se asegura de que comunidad_id y vecino_id sean NULL
     * para que sea visible globalmente.
     */
    @PostMapping("/maestro/guardar")
    public String guardarMaestro(@ModelAttribute ConceptoCobro concepto,
                                 @RequestParam(required = false) Long cuentaContableId) {

        if (concepto.getImporte() == null) concepto.setImporte(BigDecimal.ZERO);
        if (concepto.getMesInicio() == null) concepto.setMesInicio(1);

        if (cuentaContableId != null) {
            cuentaRepository.findById(cuentaContableId).ifPresent(concepto::setCuentaContable);
        }

        // VITAL: Para que sea GLOBAL, estos campos deben ser null
        concepto.setVecino(null);
        concepto.setComunidad(null);

        log.info("Guardando concepto maestro global: {}", concepto.getDescripcion());
        conceptoRepository.save(concepto);
        return "redirect:/conceptos/maestro";
    }

    @PostMapping("/maestro/eliminar/{id}")
    public String eliminarMaestro(@PathVariable Long id) {
        conceptoRepository.deleteById(id);
        return "redirect:/conceptos/maestro";
    }

    /**
     * Gestiona los conceptos asignados a un vecino específico.
     * CORRECCIÓN GTI: Se eliminan filtros de presupuesto para mostrar TODO el catálogo maestro.
     */
    @GetMapping("/vecino/{vecinoId}")
    public String gestionarConceptosVecino(@PathVariable Long vecinoId, Model model) {
        Vecino vecino = vecinoRepository.findById(vecinoId)
                .orElseThrow(() -> new RuntimeException("Vecino no encontrado"));

        Comunidad comunidad = vecino.getComunidad();

        // Obtenemos los ya asignados al vecino
        List<ConceptoCobro> conceptosDelVecino = conceptoRepository.findByVecino(vecino);

        // Obtenemos TODOS los maestros globales (sin filtrar por presupuesto)
        List<ConceptoCobro> todosLosMaestros = conceptoRepository.findByVecinoIsNull();

        model.addAttribute("activePage", "vecinos");
        model.addAttribute("vecino", vecino);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("conceptos", conceptosDelVecino);
        model.addAttribute("conceptosMaestros", todosLosMaestros); // Combo ahora completo

        // Cuentas contables para asignación manual si fuera necesario
        model.addAttribute("cuentasIngreso", cuentaRepository.findByComunidadIdAndTipo(comunidad.getId(), TipoCuenta.INGRESO));
        model.addAttribute("cuentasPasivo", cuentaRepository.findByComunidadIdAndTipo(comunidad.getId(), TipoCuenta.PASIVO));

        ConceptoCobro nuevo = new ConceptoCobro();
        nuevo.setMesInicio(LocalDate.now().getMonthValue());
        model.addAttribute("nuevoConcepto", nuevo);

        return "conceptos-vecino";
    }

    /**
     * Asigna un concepto a un vecino.
     * Incluye la lógica de personalización de descripción con el nombre de la FINCA.
     */
    @PostMapping("/guardar")
    public String guardarConcepto(@ModelAttribute ConceptoCobro concepto,
                                  @RequestParam Long vecinoId,
                                  @RequestParam(required = false) Long cuentaContableId,
                                  RedirectAttributes ra) {
        try {
            Vecino v = vecinoRepository.findById(vecinoId).orElseThrow();

            // Vinculamos el concepto al vecino y a su comunidad
            concepto.setVecino(v);
            concepto.setComunidad(v.getComunidad());

            // LÓGICA GTI: Si el concepto es de cuota, añadimos la vivienda a la descripción automáticamente
            if (concepto.getDescripcion() != null && concepto.getDescripcion().toLowerCase().contains("presupuesto")) {
                String vivienda = (v.getVivienda() != null) ? v.getVivienda() : "";
                concepto.setDescripcion("CUOTA COMUNIDAD " + vivienda);
            }

            if (concepto.getMesInicio() == null) {
                concepto.setMesInicio(LocalDate.now().getMonthValue());
            }

            if (cuentaContableId != null) {
                cuentaRepository.findById(cuentaContableId).ifPresent(concepto::setCuentaContable);
            }

            log.info("Asignando concepto a vecino {}: {}", v.getNombre(), concepto.getDescripcion());
            conceptoRepository.save(concepto);
            ra.addFlashAttribute("exito", "Concepto asignado al propietario con éxito.");

        } catch (Exception e) {
            log.error("Error al asignar concepto", e);
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
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

        log.info("Actualizando plantilla maestra ID: {}", id);
        conceptoRepository.save(c);
        return "redirect:/conceptos/maestro";
    }
}