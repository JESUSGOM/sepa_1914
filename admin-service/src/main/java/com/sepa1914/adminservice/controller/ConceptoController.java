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
 * REFACTURADO GTI:
 * - Soporte completo IVA / IGIC / IPSI
 * - Conceptos maestros globales
 * - Cuentas 700 / 731 / 477 visibles
 * - Sin filtros restrictivos
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
     * ============================================================
     * MAESTRO GLOBAL
     * ============================================================
     */
    @GetMapping("/maestro")
    public String listarMaestro(
            @RequestParam(required = false) Long comunidadId,
            Model model
    ) {

        /**
         * ============================================================
         * GTI FIX DEFINITIVO
         * ============================================================
         *
         * Si no viene comunidadId por URL,
         * usamos directamente la comunidad 10
         * para cargar correctamente:
         *
         * - 70000001
         * - 731xxxx
         * - 759xxxx
         * - 477xxxx
         *
         * ============================================================
         */
        Long idParaCuentas =(comunidadId != null)? comunidadId: 10L;
        log.error("COMUNIDAD USADA PARA CARGAR CUENTAS: {}", idParaCuentas);

        /**
         * ============================================================
         * CONCEPTOS MAESTROS
         * ============================================================
         */
        List<ConceptoCobro> maestros = conceptoRepository.findByVecinoIsNull();

        /**
         * ============================================================
         * CUENTAS DE INGRESO
         *
         * MOSTRAMOS:
         *
         * 700 -> Honorarios
         * 731 -> Cuotas
         * 759 -> Otros ingresos
         *
         * ============================================================
         */
        List<CuentaContable> ingresos =
                cuentaRepository
                        .findByComunidadId(idParaCuentas)
                        .stream()
                        .filter(c ->
                                c.getCodigo().startsWith("700")
                                        ||
                                        c.getCodigo().startsWith("731")
                                        ||
                                        c.getCodigo().startsWith("759")
                        )
                        .sorted((a, b) ->
                                a.getCodigo().compareTo(b.getCodigo()))
                        .collect(Collectors.toList());

        /**
         * ============================================================
         * CUENTAS IMPUESTO REPERCUTIDO
         *
         * 477 IVA
         * 4777 IGIC
         * IPSI
         *
         * ============================================================
         */
        List<CuentaContable> pasivos =
                cuentaRepository
                        .findByComunidadId(idParaCuentas)
                        .stream()
                        .filter(c ->
                                c.getCodigo().startsWith("477")
                        )
                        .sorted((a, b) ->
                                a.getCodigo().compareTo(b.getCodigo()))
                        .collect(Collectors.toList());

        /**
         * ============================================================
         * LOGS DEBUG
         * ============================================================
         */
        log.error("TOTAL CUENTAS INGRESO: {}", ingresos.size());

        ingresos.forEach(c ->log.error("INGRESO -> {} {}", c.getCodigo(), c.getNombre()));

        pasivos.forEach(c ->log.error("PASIVO -> {} {}", c.getCodigo(), c.getNombre()));

        /**
         * ============================================================
         * MODEL
         * ============================================================
         */
        model.addAttribute("cuentasIngreso", ingresos);

        model.addAttribute("cuentasPasivo", pasivos);

        model.addAttribute("conceptosMaestros", maestros);

        model.addAttribute("activePage", "maestro-conceptos");

        /**
         * ============================================================
         * NUEVO CONCEPTO
         * ============================================================
         */
        ConceptoCobro nuevoMaestro = new ConceptoCobro();

        nuevoMaestro.setMesInicio(LocalDate.now().getMonthValue());

        nuevoMaestro.setActivo(true);

        /**
         * ============================================================
         * VALORES FISCALES POR DEFECTO
         * ============================================================
         */
        nuevoMaestro.setTipoImpuesto(TipoImpuesto.EXENTO);

        nuevoMaestro.setPorcentajeImpuesto(BigDecimal.ZERO);

        model.addAttribute("nuevoMaestro",nuevoMaestro);

        return "conceptos-maestro";
    }

    /**
     * ============================================================
     * GUARDAR MAESTRO
     * ============================================================
     */
    @PostMapping("/maestro/guardar")
    public String guardarMaestro(
            @ModelAttribute ConceptoCobro concepto,
            @RequestParam(required = false)
            Long cuentaContableId
    ) {
        if (concepto.getImporte() == null) {
            concepto.setImporte(BigDecimal.ZERO);
        }
        if (concepto.getMesInicio() == null) {
            concepto.setMesInicio(1);
        }
        /**
         * GTI:
         * Seguridad fiscal
         */
        if (concepto.getTipoImpuesto() == null) {
            concepto.setTipoImpuesto(
                    TipoImpuesto.EXENTO
            );
        }
        if (concepto.getPorcentajeImpuesto() == null) {
            concepto.setPorcentajeImpuesto(
                    BigDecimal.ZERO
            );
        }
        if (cuentaContableId != null) {
            cuentaRepository
                    .findById(cuentaContableId)
                    .ifPresent(concepto::setCuentaContable);
        }
        /**
         * GLOBAL
         */
        concepto.setVecino(null);
        concepto.setComunidad(null);
        log.info(
                "Guardando concepto maestro global: {}",
                concepto.getDescripcion()
        );
        conceptoRepository.save(concepto);
        return "redirect:/conceptos/maestro";
    }

    /**
     * ============================================================
     * ELIMINAR MAESTRO
     * ============================================================
     */
    @PostMapping("/maestro/eliminar/{id}")
    public String eliminarMaestro(
            @PathVariable Long id
    ) {
        conceptoRepository.deleteById(id);
        return "redirect:/conceptos/maestro";
    }

    /**
     * ============================================================
     * CONCEPTOS VECINO
     * ============================================================
     */
    @GetMapping("/vecino/{vecinoId}")
    public String gestionarConceptosVecino(
            @PathVariable Long vecinoId,
            Model model
    ) {
        Vecino vecino = vecinoRepository.findById(vecinoId).orElseThrow(() ->
                new RuntimeException("Vecino no encontrado"));

        Comunidad comunidad = vecino.getComunidad();

        List<ConceptoCobro> conceptosDelVecino =
                conceptoRepository.findByVecino(vecino);
        List<ConceptoCobro> todosLosMaestros =
                conceptoRepository.findByVecinoIsNull();
        model.addAttribute("activePage", "vecinos");
        model.addAttribute("vecino", vecino);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("conceptos",conceptosDelVecino);
        model.addAttribute("conceptosMaestros",todosLosMaestros);

        /**
         * GTI FIX:
         * Mostrar TODAS las cuentas 7xx
         */
        model.addAttribute("cuentasIngreso",cuentaRepository
                .findByComunidadId(comunidad.getId())
                .stream()
                .filter(c ->
                        c.getCodigo().startsWith("7"))
                .sorted((a, b) ->
                        a.getCodigo()
                                .compareTo(b.getCodigo()))
                .collect(Collectors.toList())
        );

        /**
         * GTI FIX:
         * Mostrar IVA/IGIC/IPSI
         */

        model.addAttribute("cuentasPasivo",cuentaRepository
                .findByComunidadId(comunidad.getId())
                .stream()
                .filter(c ->
                        c.getCodigo().startsWith("477"))
                .sorted((a, b) ->
                        a.getCodigo()
                                .compareTo(b.getCodigo()))
                .collect(Collectors.toList())
        );

        ConceptoCobro nuevo = new ConceptoCobro();

        nuevo.setMesInicio(LocalDate.now().getMonthValue());

        nuevo.setTipoImpuesto(TipoImpuesto.EXENTO);

        nuevo.setPorcentajeImpuesto(BigDecimal.ZERO);

        model.addAttribute("nuevoConcepto",nuevo);

        return "conceptos-vecino";
    }

    /**
     * ============================================================
     * GUARDAR CONCEPTO VECINO
     * ============================================================
     */
    @PostMapping("/guardar")
    public String guardarConcepto(
            @ModelAttribute ConceptoCobro concepto,

            @RequestParam Long vecinoId,

            @RequestParam(required = false)
            Long cuentaContableId,

            RedirectAttributes ra
    ) {

        try {

            Vecino v =
                    vecinoRepository.findById(vecinoId)
                            .orElseThrow();

            concepto.setVecino(v);

            concepto.setComunidad(
                    v.getComunidad()
            );

            /**
             * GTI:
             * Descripción automática
             */

            if (concepto.getDescripcion() != null
                    &&
                    concepto.getDescripcion()
                            .toLowerCase()
                            .contains("presupuesto")) {

                String vivienda =
                        (v.getVivienda() != null)
                                ? v.getVivienda()
                                : "";

                concepto.setDescripcion(
                        "CUOTA COMUNIDAD " + vivienda
                );
            }

            if (concepto.getMesInicio() == null) {

                concepto.setMesInicio(
                        LocalDate.now().getMonthValue()
                );
            }

            /**
             * FISCALIDAD
             */

            if (concepto.getTipoImpuesto() == null) {

                concepto.setTipoImpuesto(
                        TipoImpuesto.EXENTO
                );
            }

            if (concepto.getPorcentajeImpuesto() == null) {

                concepto.setPorcentajeImpuesto(
                        BigDecimal.ZERO
                );
            }

            if (cuentaContableId != null) {

                cuentaRepository
                        .findById(cuentaContableId)
                        .ifPresent(concepto::setCuentaContable);
            }

            log.info(
                    "Asignando concepto a vecino {}: {}",
                    v.getNombre(),
                    concepto.getDescripcion()
            );

            conceptoRepository.save(concepto);

            ra.addFlashAttribute(
                    "exito",
                    "Concepto asignado correctamente."
            );

        } catch (Exception e) {

            log.error(
                    "Error al asignar concepto",
                    e
            );

            ra.addFlashAttribute(
                    "error",
                    "Error: " + e.getMessage()
            );
        }

        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    /**
     * ============================================================
     * ELIMINAR CONCEPTO
     * ============================================================
     */
    @PostMapping("/eliminar/{id}")
    public String eliminarConcepto(
            @PathVariable Long id,
            @RequestParam Long vecinoId
    ) {

        conceptoRepository.deleteById(id);

        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    /**
     * ============================================================
     * TOGGLE ESTADO
     * ============================================================
     */
    @PostMapping("/toggle-estado/{id}")
    public String toggleEstadoConcepto(
            @PathVariable Long id,
            @RequestParam Long vecinoId
    ) {

        ConceptoCobro c =
                conceptoRepository.findById(id)
                        .orElseThrow();

        c.setActivo(!c.isActivo());

        conceptoRepository.save(c);

        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    /**
     * ============================================================
     * EDITAR IMPORTE
     * ============================================================
     */
    @PostMapping("/editar-importe/{id}")
    public String editarImporte(
            @PathVariable Long id,

            @RequestParam BigDecimal nuevoImporte,

            @RequestParam Long vecinoId
    ) {

        ConceptoCobro c =
                conceptoRepository.findById(id)
                        .orElseThrow();

        c.setImporte(nuevoImporte);

        conceptoRepository.save(c);

        return "redirect:/conceptos/vecino/" + vecinoId;
    }

    /**
     * ============================================================
     * ACTUALIZAR MAESTRO
     * ============================================================
     */
    @PostMapping("/maestro/actualizar")
    public String actualizarMaestro(

            @RequestParam Long id,

            @RequestParam String descripcion,

            @RequestParam(required = false)
            Long cuentaContableId,

            @RequestParam
            ConceptoCobro.Periodicidad periodicidad,

            @RequestParam(
                    required = false,
                    defaultValue = "1"
            )
            Integer mesInicio,

            /**
             * NUEVO
             */

            @RequestParam(
                    defaultValue = "EXENTO"
            )
            TipoImpuesto tipoImpuesto,

            @RequestParam(
                    defaultValue = "0"
            )
            BigDecimal porcentajeImpuesto
    ) {

        ConceptoCobro c =
                conceptoRepository.findById(id)
                        .orElseThrow();

        c.setDescripcion(descripcion);

        c.setPeriodicidad(periodicidad);

        c.setMesInicio(mesInicio);

        /**
         * NUEVO:
         * Fiscalidad
         */

        c.setTipoImpuesto(tipoImpuesto);

        c.setPorcentajeImpuesto(
                porcentajeImpuesto
        );
        if (cuentaContableId != null) {cuentaRepository
            .findById(cuentaContableId)
            .ifPresent(c::setCuentaContable);
        }
        log.info(
                "Actualizando plantilla maestra ID: {}",
                id
        );
        conceptoRepository.save(c);
        return "redirect:/conceptos/maestro";
    }
}